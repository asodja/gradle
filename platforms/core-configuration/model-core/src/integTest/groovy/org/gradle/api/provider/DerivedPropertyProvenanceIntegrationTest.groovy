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

class DerivedPropertyProvenanceIntegrationTest extends AbstractIntegrationSpec {
    def 'derived checkpoints survive cache reuse isolated=#isolated missing=#missing'() {
        given:
        buildFile << """
            import org.gradle.api.internal.provider.Providers
            import org.gradle.api.internal.provider.PropertyProvenanceTransport
            abstract class CheckTask extends DefaultTask {
                @Internal abstract Property<String> getValue()
                @Internal abstract MapProperty<String, String> getMapping()
                @Internal List<Provider<?>> derived
                @Internal List<String> expected
                @TaskAction void check() {
                    println 'MATCH=' + (expected == derived.collect { Base64.encoder.encodeToString(PropertyProvenanceTransport.encodeCheckpoint(it)) })
                    derived.each { provider ->
                        println provider.configurationTrace
                        try { println 'VALUE=' + provider.get() } catch (IllegalStateException ex) { println ex.message }
                    }
                }
            }
            tasks.register('checkDerived', CheckTask) {
                value.set(${missing} ? Providers.notDefined() : Providers.of('present'))
                mapping.set(${missing} ? Providers.notDefined() : Providers.of([key: 'present']))
                derived = [value.map { it }, value.filter { true }, value.flatMap { Providers.of(it) },
                    value.orElse(Providers.notDefined()), value.zip(Providers.of('right')) { left, right -> left + right },
                    mapping.getting('key'), mapping.keySet(), value.shallowCopy().map { it }]
                expected = derived.collect { Base64.encoder.encodeToString(PropertyProvenanceTransport.encodeCheckpoint(it)) }
            }
        """

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        succeeds('checkDerived')

        then:
        output.contains('MATCH=true')
        output.contains('flatMap by')
        output.contains('mapEntry by')
        output.contains(missing ? 'Configuration of' : 'VALUE=present')

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true', "-Dorg.gradle.unsafe.isolated-projects=${isolated}")
        succeeds('checkDerived')

        then:
        output.contains('Reusing configuration cache')
        output.contains('MATCH=true')
        output.contains('zip by')
        output.contains(missing ? 'Configuration of' : 'VALUE=present')

        where:
        [isolated, missing] << [[false, true], [false, true]].combinations()
    }

    def 'required task input validation includes provenance enabled=#enabled'() {
        given:
        enableProblemsApiCheck()
        buildFile << """
            abstract class CheckTask extends DefaultTask {
                @Input abstract Property<String> getValue()
                @TaskAction void check() {}
            }
            tasks.register('checkMissing', CheckTask)
        """

        when:
        executer.withArguments("-Dorg.gradle.internal.property-provenance=${enabled}")
        fails('checkMissing')

        then:
        failure.assertHasErrorOutput("This property isn't marked as optional and no value has been configured")
        verifyAll(receivedProblem) {
            fqid == 'validation:property-validation:value-not-set'
            solutions == ["Assign a value to 'value'", "Mark property 'value' as optional"]
        }
        errorOutput.contains('Configuration of') == enabled

        where:
        enabled << [false, true]
    }

    def 'task evaluation failure retains its cause and reports diagnostic context'() {
        given:
        buildFile << """
            abstract class CheckTask extends DefaultTask {
                @Internal abstract Property<String> getValue()
                @TaskAction void check() {
                    value.map { throw new UnsupportedOperationException('original transform problem') }.get()
                }
            }
            tasks.register('checkFailure', CheckTask) { value = 'configured' }
        """

        when:
        executer.withArguments('-Dorg.gradle.internal.property-provenance=true', '--stacktrace')
        fails('checkFailure')

        then:
        failure.assertHasCause('original transform problem')
        errorOutput.contains('Configuration of')
        errorOutput.contains('map by')
    }
    def 'derived changing values retain producer dependencies on cache reuse'() {
        given:
        file('seed.txt').text = 'first'
        buildFile << """
            abstract class ProduceTask extends DefaultTask {
                @InputFile abstract RegularFileProperty getSeed()
                @OutputFile abstract RegularFileProperty getResult()
                @TaskAction void produce() { result.get().asFile.text = seed.get().asFile.text }
            }
            abstract class ConsumeTask extends DefaultTask {
                @Input abstract Property<String> getValue()
                @Internal Provider<String> derived
                @TaskAction void consume() {
                    println 'DERIVED=' + derived.get()
                    println derived.configurationTrace
                }
            }
            def producer = tasks.register('produce', ProduceTask) {
                seed = layout.projectDirectory.file('seed.txt')
                result = layout.buildDirectory.file('result.txt')
            }
            tasks.register('consume', ConsumeTask) {
                value = producer.flatMap { it.result }.map { it.asFile.text }
                derived = value.map { it + '-mapped' }.orElse('fallback')
            }
        """

        when:
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true')
        succeeds('consume')

        then:
        result.assertTasksExecuted(':produce', ':consume')
        output.contains('DERIVED=first-mapped')
        output.contains('orElse by')

        when:
        file('seed.txt').text = 'second'
        executer.withArguments('--configuration-cache', '-Dorg.gradle.internal.property-provenance=true')
        succeeds('consume')

        then:
        output.contains('Reusing configuration cache')
        result.assertTasksExecuted(':produce', ':consume')
        output.contains('DERIVED=second-mapped')
        output.contains('map by')
    }

}
