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

class PropertyBindingProvenanceIntegrationTest extends AbstractIntegrationSpec {
    def 'named missing property reports bound transformations across cache reuse isolated=#isolated'() {
        given:
        file('buildSrc/build.gradle') << "plugins { id 'java-gradle-plugin' }; gradlePlugin { plugins { demo { id = 'demo.binding'; implementationClass = 'Demo' } } }"
        file('buildSrc/src/main/java/Demo.java') << '''
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            public class Demo implements Plugin<Project> {
                public abstract static class Check extends DefaultTask {
                    @Internal public abstract Property<String> getSource();
                    @Internal public abstract Property<String> getValue();
                    private Provider<String> chain;
                    @Internal public Provider<String> getChain() { return chain; }
                    public void setChain(Provider<String> value) { chain = value; }
                    @TaskAction public void run() { getValue().get(); }
                }
                public void apply(Project project) {
                    project.getTasks().register("missing", Check.class, task -> {
                        task.getSource().set("seed");
                        task.setChain(task.getSource().map(value -> value.trim()));
                    });
                }
            }
        '''
        file('build.gradle.kts') << '''
            import Demo.Check
            plugins { id("demo.binding") }
            tasks.named<Check>("missing") {
                source = "configured"
                value.set(chain.map { it.uppercase() }.filter { false })
            }
        '''

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        fails('missing')

        then:
        errorOutput.contains("Cannot query the value of task ':missing' property 'value'")
        errorOutput.contains('filter by build script')
        errorOutput.contains('map by build script')
        errorOutput.contains("map by plugin 'demo.binding'")
        errorOutput.contains(file('buildSrc/src/main/java/Demo.java').absolutePath)
        errorOutput.contains(file('build.gradle.kts').absolutePath)
        def first = errorOutput.substring(errorOutput.indexOf('  Configuration of'), errorOutput.indexOf('* Try:')).trim()

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        fails('missing')

        then:
        output.contains('Reusing configuration cache')
        errorOutput.substring(errorOutput.indexOf('  Configuration of'), errorOutput.indexOf('* Try:')).trim() == first

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=false', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        fails('missing')

        then:
        errorOutput.contains("Cannot query the value of task ':missing' property 'value'")
        !errorOutput.contains('Configuration of')

        where:
        isolated << [false, true]
    }
}
