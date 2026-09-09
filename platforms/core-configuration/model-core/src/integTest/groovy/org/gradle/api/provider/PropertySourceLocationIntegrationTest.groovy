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

class PropertySourceLocationIntegrationTest extends AbstractIntegrationSpec {
    def 'Java and Kotlin mutation lines survive configuration cache isolated=#isolated assignment=#assignment'() {
        given:
        file('buildSrc/build.gradle') << "plugins { id 'java' }"
        file('buildSrc/src/main/java/Locations.java') << '''
            import org.gradle.api.*;
            import org.gradle.api.provider.*;
            import org.gradle.api.tasks.*;
            import org.gradle.api.internal.provider.ProvenanceAware;
            import java.util.*;
            public class Locations implements Plugin<Project> {
                public abstract static class Check extends DefaultTask {
                    @Internal public abstract Property<String> getScalar();
                    @Internal public abstract ListProperty<String> getItems();
                    @Internal public abstract SetProperty<String> getNames();
                    @Internal public abstract MapProperty<String, String> getMapping();
                    @TaskAction public void check() {
                        for (Object property : Arrays.asList(getScalar(), getItems(), getNames(), getMapping())) {
                            System.out.println(((ProvenanceAware) property).getConfigurationTrace());
                        }
                        System.out.println("VALUES=" + getScalar().get() + getItems().get() + getNames().get() + getMapping().get());
                    }
                }
                public void apply(Project project) {
                    project.getTasks().register("checkLocations", Check.class, task -> {
                        task.getScalar().convention("java");
                        task.getItems().set(Arrays.asList("java"));
                        task.getNames().set(Arrays.asList("java"));
                        task.getMapping().set(Collections.singletonMap("java", "value"));
                    });
                }
            }
        '''
        file('build.gradle.kts') << '''
            apply<Locations>()
            tasks.named<Locations.Check>("checkLocations") {
                scalar.set("kotlin")
                items.add("kotlin")
                names.addAll(listOf("kotlin"))
                mapping.put("kotlin", "value")
            }
        '''
        if (assignment) {
            def script = file('build.gradle.kts')
            script.text = script.text.replace('scalar.set("kotlin")', 'scalar = "kotlin"')
                .replace('items.add("kotlin")', 'items = listOf("java", "kotlin")')
                .replace('names.addAll(listOf("kotlin"))', 'names = listOf("java", "kotlin")')
                .replace('mapping.put("kotlin", "value")', 'mapping = mapOf("java" to "value", "kotlin" to "value")')
        }
        def javaLines = ["task.getItems().set", "task.getNames().set", "task.getMapping().set"].collect {
            sourceLine('buildSrc/src/main/java/Locations.java', it)
        }
        def kotlinLines = (assignment ? ['scalar =', 'items =', 'names =', 'mapping ='] : ['scalar.set', 'items.add', 'names.addAll', 'mapping.put']).collect {
            sourceLine('build.gradle.kts', it)
        }

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        succeeds('checkLocations')

        then:
        assignment || javaLines.every { output.contains("(${file('buildSrc/src/main/java/Locations.java').absolutePath}:$it)") }
        kotlinLines.every { output.contains("(${file('build.gradle.kts').absolutePath}:$it)") }
        output.contains('VALUES=kotlin[java, kotlin][java, kotlin]{java=value, kotlin=value}')

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        succeeds('checkLocations')

        then:
        output.contains('Reusing configuration cache')
        assignment || javaLines.every { output.contains("(${file('buildSrc/src/main/java/Locations.java').absolutePath}:$it)") }
        kotlinLines.every { output.contains("(${file('build.gradle.kts').absolutePath}:$it)") }
        output.contains('VALUES=kotlin[java, kotlin][java, kotlin]{java=value, kotlin=value}')

        where:
        [isolated, assignment] << [[false, true], [false, true]].combinations()
    }

    def 'Kotlin locations are absent when diagnostics are disabled'() {
        given:
        file('build.gradle.kts') << '''
            import org.gradle.api.internal.provider.ProvenanceAware
            val value = objects.property(String::class.java)
            value.set("value")
            println("TRACKED=" + (value is ProvenanceAware))
            println("VALUE=" + value.get())
        '''

        when:
        executer.withArguments('-Dorg.gradle.internal.property-provenance=false')
        succeeds('help')

        then:
        output.contains('TRACKED=false')
        output.contains('VALUE=value')
    }

    def 'applied scripts with the same filename resolve independently in #console console output'() {
        given:
        file('build.gradle.kts') << '''
            import org.gradle.api.internal.provider.ProvenanceAware
            val tracked = objects.property(String::class.java)
            extensions.add("tracked", tracked)
            apply(from = "scripts with spaces/build.gradle.kts")
            println((tracked as ProvenanceAware).configurationTrace)
            tracked.disallowChanges()
            try {
                tracked.set("rejected")
            } catch (failure: IllegalStateException) {
                println(failure.message)
            }
            tracked.finalizeValue()
            try {
                tracked.set("after finalization")
            } catch (failure: IllegalStateException) {
                println(failure.message)
            }
        '''
        file('scripts with spaces/build.gradle.kts') << '''
            val tracked = extensions.getByName("tracked") as Property<String>
            tracked.set("accepted")
        '''
        def source = file('scripts with spaces/build.gradle.kts').absolutePath + ':' + sourceLine('scripts with spaces/build.gradle.kts', 'tracked.set')
        def rejected = file('build.gradle.kts').absolutePath + ':' + sourceLine('build.gradle.kts', 'tracked.set')
        def finalized = sourceLine('build.gradle.kts', 'tracked.set("after finalization")')

        when:
        executer.withArguments('-Dorg.gradle.internal.property-provenance=true', "--console=${console}")
        succeeds('help')

        then:
        output.contains("set by applied script (${source})")
        output.contains("failed set by build script (${rejected})")
        output.contains("failed set by unknown origin (build.gradle.kts:${finalized})")
        !output.contains('Local configuration only')

        where:
        console << ['plain', 'rich']
    }

    private int sourceLine(String path, String text) {
        file(path).readLines().findIndexOf { it.contains(text) } + 1
    }
}
