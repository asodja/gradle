/*
 * Copyright 2026 Gradle and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProviderSourceLocationIntegrationTest extends AbstractIntegrationSpec {
    def 'Java and Kotlin provider declarations and local plugin sources survive cache reuse isolated=#isolated pluginBuild=#pluginBuild'() {
        given:
        file("${pluginBuild}/settings.gradle") << "rootProject.name = 'location-plugins'"
        file("${pluginBuild}/build.gradle") << """
            plugins { id 'java-gradle-plugin' }
            gradlePlugin { plugins { locations { id = 'example.locations'; implementationClass = 'example.Locations' } } }
        """
        if (pluginBuild == 'build-logic') {
            settingsFile << "pluginManagement { includeBuild('build-logic') }"
        }
        file("${pluginBuild}/src/main/java/example/Locations.java") << '''
            package example;
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.internal.provider.*;
            import java.util.*;
            public class Locations implements Plugin<Project> {
                public abstract static class Check extends DefaultTask {
                    @Internal public abstract Property<String> getValue();
                    @Internal public abstract MapProperty<String, String> getMapping();
                    public List<Provider<?>> chains;
                    public List<String> expected;
                    @Internal public List<Provider<?>> getChains() { return chains; }
                    @Internal public List<String> getExpected() { return expected; }
                    public void setExpected(List<String> value) { expected = value; }
                    @TaskAction public void check() {
                        List<String> actual = new ArrayList<>();
                        for (Provider<?> chain : chains) {
                            String trace = ((ProvenanceAware) chain).getConfigurationTrace();
                            actual.add(trace);
                            System.out.println(trace);
                            try { chain.get(); } catch (IllegalStateException failure) { System.out.println(failure.getMessage()); }
                        }
                        if (!actual.equals(expected)) { throw new AssertionError("Checkpoint changed"); }
                        System.out.println("MATCH=true");
                    }
                }
                public void apply(Project project) {
                    project.getTasks().register("checkLocations", Check.class, task -> {
                        task.getValue().set("value");
                        task.getMapping().set(Collections.singletonMap("key", "value"));
                        Provider<String> filtered = task.getValue()
                            .map(value -> value)
                            .filter(value -> false);
                        task.chains = new ArrayList<>(Arrays.asList(filtered,
                            task.getValue().flatMap(value -> Providers.notDefined()),
                            task.getValue().orElse("fallback"),
                            task.getValue().zip(Providers.of("right"), (left, right) -> left + right),
                            project.getProviders().zip(task.getValue(), Providers.of("factory"), (left, right) -> left + right),
                            task.getMapping().getting("key"),
                            task.getMapping().keySet()));
                    });
                }
            }
        '''
        file('build.gradle.kts') << '''
            import example.Locations
            import org.gradle.api.internal.provider.ProvenanceAware
            plugins { id("example.locations") }
            tasks.named<Locations.Check>("checkLocations") {
                chains.add(value.map { it }.filter { false })
                expected = chains.map { (it as ProvenanceAware).configurationTrace }
            }
        '''
        def javaPath = file("${pluginBuild}/src/main/java/example/Locations.java").absolutePath
        def javaLines = ['.map(value', '.filter(value', '.flatMap(value', '.orElse(', '.zip(Providers', '.zip(task', '.getting(', '.keySet('].collect {
            file("${pluginBuild}/src/main/java/example/Locations.java").readLines().findIndexOf { line -> line.contains(it) } + 1
        }
        def kotlinLine = file('build.gradle.kts').readLines().findIndexOf { it.contains('chains.add') } + 1

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        succeeds('checkLocations')

        then:
        output.contains('MATCH=true')
        javaLines.every { output.contains("(${javaPath}:$it)") }
        output.contains("map by build script (${file('build.gradle.kts').absolutePath}:$kotlinLine)")
        output.contains("filter by build script (${file('build.gradle.kts').absolutePath}:$kotlinLine)")
        output.contains('set by ')
        output.contains('selection unknown')
        output.contains('left input shown; other input not traced')

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        succeeds('checkLocations')

        then:
        output.contains('Reusing configuration cache')
        output.contains('MATCH=true')
        javaLines.every { output.contains("(${javaPath}:$it)") }
        output.contains("filter by build script (${file('build.gradle.kts').absolutePath}:$kotlinLine)")

        where:
        [isolated, pluginBuild] << [[false, true], ['buildSrc', 'build-logic']].combinations()
    }
}
