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

class PropertyProvenanceTransportIntegrationTest extends AbstractIntegrationSpec {
    def 'configuration cache separates enabled and disabled provenance'() {
        given:
        buildFile << """
            import org.gradle.api.internal.provider.ProvenanceAware
            abstract class CheckTask extends DefaultTask {
                @Internal abstract Property<String> getValue()
                @TaskAction void check() {
                    println 'TRACKED=' + (value instanceof ProvenanceAware)
                }
            }
            tasks.register('checkProvenance', CheckTask) { value = 'value' }
        """

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true')
        succeeds('checkProvenance')

        then:
        output.contains('TRACKED=true')
        output.contains('Configuration cache entry stored')

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=false')
        succeeds('checkProvenance')

        then:
        output.contains('TRACKED=false')
        output.contains('Configuration cache entry stored')

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true')
        succeeds('checkProvenance')

        then:
        output.contains('TRACKED=true')
        output.contains('Reusing configuration cache')
    }

    def 'cache reuse preserves changing provider plans producer dependencies and property aliases'() {
        given:
        file('seed.txt').text = 'first'
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true')
        buildFile << """
            import org.gradle.api.internal.provider.PropertyProvenanceTransport
            abstract class ProduceTask extends DefaultTask {
                @InputFile abstract RegularFileProperty getSeed()
                @OutputFile abstract RegularFileProperty getOutputFile()
                @TaskAction void produce() { outputFile.get().asFile.text = seed.get().asFile.text }
            }
            abstract class ConsumeTask extends DefaultTask {
                @Input abstract Property<String> getValue()
                @Internal Provider<String> alias
                @Internal Provider<String> snapshot
                @Internal String expected
                @TaskAction void consume() {
                    println 'VALUE=' + value.get()
                    println 'SNAPSHOT=' + snapshot.get()
                    println 'ALIAS=' + alias.is(value)
                    println 'TRACE_MATCH=' + (expected == Base64.encoder.encodeToString(PropertyProvenanceTransport.encodeCheckpoint(value)))
                    println snapshot.configurationTrace
                }
            }
            def producer = tasks.register('produce', ProduceTask) {
                seed = layout.projectDirectory.file('seed.txt')
                outputFile = layout.buildDirectory.file('result.txt')
            }
            tasks.register('consume', ConsumeTask) {
                value = producer.flatMap { it.outputFile }.map { it.asFile.text }
                value.replace { previous -> previous.map { it + '-updated' } }
                alias = value
                snapshot = value.shallowCopy()
                expected = Base64.encoder.encodeToString(PropertyProvenanceTransport.encodeCheckpoint(value))
            }
        """

        when:
        succeeds('consume')

        then:
        result.assertTasksExecuted(':produce', ':consume')
        output.contains('VALUE=first-updated')
        output.contains('SNAPSHOT=first-updated')
        output.contains('ALIAS=true')
        output.contains('TRACE_MATCH=true')

        when:
        file('seed.txt').text = 'second'
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true')
        succeeds('consume')

        then:
        output.contains('Reusing configuration cache')
        result.assertTasksExecuted(':produce', ':consume')
        output.contains('VALUE=second-updated')
        output.contains('SNAPSHOT=second-updated')
        output.contains('ALIAS=true')
        output.contains('TRACE_MATCH=true')
    }

    def 'property and snapshot provenance survives cache store and reuse isolated=#isolated missing=#missing'() {
        given:
        executer.withArguments('-Dorg.gradle.internal.property-provenance=true', '--configuration-cache',
            "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        buildFile << """
            import org.gradle.api.internal.provider.PropertyProvenanceTransport

            abstract class ReportTask extends DefaultTask {
                @Internal abstract Property<String> getScalar()
                @Internal abstract ListProperty<String> getItems()
                @Internal abstract SetProperty<String> getEntries()
                @Internal abstract MapProperty<String, String> getMapping()
                @Internal List<Provider<?>> copies
                @Internal List<String> expected

                List<Provider<?>> targets() { [scalar, items, entries, mapping] + copies }

                static List<String> checkpoints(List<Provider<?>> targets) {
                    targets.collect { Base64.encoder.encodeToString(PropertyProvenanceTransport.encodeCheckpoint(it)) }
                }

                @TaskAction void report() {
                    println 'CHECKPOINTS_MATCH=' + (checkpoints(targets()) == expected)
                    targets().each { target ->
                        println target.configurationTrace
                        if (${missing}) {
                            try {
                                target.get()
                            } catch (Exception failure) {
                                println 'MISSING_TRACE=' + failure.message.contains('Configuration of')
                            }
                        } else {
                            println 'PRESENT=' + target.present
                        }
                    }
                }
            }

            class ConfigureProperties implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register('report', ReportTask) { task ->
                        task.scalar.convention('convention')
                        task.items.convention(['convention'])
                        task.entries.convention(['convention'])
                        task.mapping.convention([key: 'convention'])
                        task.scalar.set(${missing} ? project.providers.provider { null } : project.providers.provider { 'value' })
                        task.items.set(${missing} ? project.providers.provider { null } : project.providers.provider { ['value'] })
                        task.entries.set(${missing} ? project.providers.provider { null } : project.providers.provider { ['value'] })
                        task.mapping.set(${missing} ? project.providers.provider { null } : project.providers.provider { [key: 'value'] })
                        task.items.add('entry')
                        task.entries.append('entry')
                        task.mapping.insert('extra', 'entry')
                        task.scalar.replace { previous -> previous.map { it + '-updated' } }
                        task.items.replace { previous -> previous.map { it } }
                        task.copies = [task.scalar.shallowCopy(), task.items.shallowCopy(), task.entries.shallowCopy(), task.mapping.shallowCopy()]
                        task.expected = ReportTask.checkpoints(task.targets())
                    }
                }
            }
            apply plugin: ConfigureProperties
        """

        when:
        succeeds('report')

        then:
        output.contains('Configuration cache entry stored')
        output.contains('CHECKPOINTS_MATCH=true')
        output.count(missing ? 'MISSING_TRACE=true' : 'PRESENT=true') == 8
        output.contains("plugin 'ConfigureProperties'")

        when:
        executer.withArguments('-Dorg.gradle.internal.property-provenance=true', '--configuration-cache',
            "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        succeeds('report')

        then:
        output.contains('Reusing configuration cache')
        output.contains('CHECKPOINTS_MATCH=true')
        output.count(missing ? 'MISSING_TRACE=true' : 'PRESENT=true') == 8
        output.contains("plugin 'ConfigureProperties'")

        where:
        [isolated, missing] << [[false, true], [false, true]].combinations()
    }
}
