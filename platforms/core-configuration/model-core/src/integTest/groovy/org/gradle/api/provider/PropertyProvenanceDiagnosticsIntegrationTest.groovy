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
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.modes.ToBeFixedForIsolatedProjects

import static org.hamcrest.Matchers.containsString

@UnsupportedWithConfigurationCache(because = 'Diagnostic transport is D2')
@ToBeFixedForIsolatedProjects(because = 'D1 includes settings-origin project configuration fixtures')
class PropertyProvenanceDiagnosticsIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument('-Dorg.gradle.internal.property-provenance=true')
    }

    def 'managed extension property name appears in configuration and failure traces through #lifecycle'() {
        given:
        buildFile << """
            abstract class Messages {
                abstract Property<String> getMessage()
            }
            def messages = extensions.create('messages', Messages)
            messages.message.set(providers.provider { null })
            def target = messages.message
            ${prepare}
            println target.configurationTrace
            target.get()
        """

        when:
        fails('help')

        then:
        output.contains("Configuration of extension 'messages' property 'message':")
        failure.assertThatCause(containsString("Configuration of extension 'messages' property 'message':"))
        !output.contains("'unnamed property'")
        !failure.error.contains("'unnamed property'")

        where:
        lifecycle        | prepare
        'mutable'        | ''
        'copy'           | 'target = target.shallowCopy()'
        'finalized copy' | 'target.finalizeValue(); target = target.shallowCopy()'
    }

    def 'missing scalar reports plugin updates and explicit source with a shadowed convention'() {
        given:
        buildFile << """
            class SourcePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def value = project.objects.property(String)
                    project.extensions.add('tracked', value)
                    value.convention('secret fallback')
                    value.set(project.providers.provider { null })
                }
            }
            class UpdatePlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tracked.replace { it.map { it + '-first' }.map { it + '-second' } }
                    project.tracked.replace { it.map { it + '-third' } }
                }
            }
            apply plugin: SourcePlugin
            apply plugin: UpdatePlugin
            tracked.get()
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('Cannot query the value of this property because it has no value available.')
        failure.assertThatCause(containsString("Configuration of 'unnamed property':"))
        failure.assertThatCause(containsString("update map by plugin 'UpdatePlugin'"))
        failure.assertThatCause(containsString("update map -> map by plugin 'UpdatePlugin'"))
        failure.assertThatCause(containsString("set by plugin 'SourcePlugin'"))
        failure.assertThatCause(containsString('Overridden'))
        !failure.error.contains('secret fallback')
    }

    def 'configuration trace is explicit lazy and survives finalization without automatic output'() {
        given:
        buildFile << """
            def value = objects.property(String)
            value.convention('secret fallback')
            value.set(providers.provider { throw new AssertionError('must not evaluate for explanation') })
            value.replace { it.map { throw new AssertionError('must not transform for explanation') } }
            ${explain ? 'println value.configurationTrace' : ''}
            def fixed = objects.property(String)
            fixed.set('secret fixed')
            fixed.finalizeValue()
            ${explain ? 'println fixed.shallowCopy().configurationTrace' : ''}
        """

        when:
        succeeds('help')

        then:
        output.contains('Configuration of') == explain
        output.count('Configuration of') == (explain ? 2 : 0)
        !output.contains('secret fallback')
        !output.contains('secret fixed')

        where:
        explain << [false, true]
    }

    def 'rejected plugin mutation preserves the original problem and accepted source'() {
        given:
        buildFile << """
            class SourcePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def value = project.objects.property(String)
                    project.extensions.add('tracked', value)
                    value.set('accepted secret')
                    value.disallowChanges()
                }
            }
            class CallerPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tracked.set('rejected secret')
                }
            }
            apply plugin: SourcePlugin
            apply plugin: CallerPlugin
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('The value for this property cannot be changed any further.')
        failure.assertThatCause(containsString("failed set by plugin 'CallerPlugin'"))
        failure.assertThatCause(containsString("set by plugin 'SourcePlugin'"))
        !failure.error.contains('accepted secret')
        !failure.error.contains('rejected secret')
    }

    def 'finalized rejection reports unknown caller without losing the configured source'() {
        given:
        buildFile << """
            def value = objects.property(String)
            value.set('secret')
            value.finalizeValue()
            value.set('rejected')
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('The value for this property is final and cannot be changed any further.')
        failure.assertThatCause(containsString('failed set by unknown caller'))
        failure.assertThatCause(containsString('set by build file'))
    }

    def 'settings configured project source survives missing finalization and copy'() {
        given:
        settingsFile << """
            gradle.beforeProject { project ->
                def value = project.objects.property(String)
                project.extensions.add('tracked', value)
                value.set(project.providers.provider { null })
            }
        """
        buildFile << """
            tracked.finalizeValue()
            tracked.shallowCopy().get()
        """

        when:
        fails('help')

        then:
        failure.assertThatCause(containsString('Configuration of'))
        failure.assertThatCause(containsString('set by settings file'))
        failure.assertThatCause(containsString("scope 'settings'"))
    }

    def 'disabled failures have the original message and no provenance report'() {
        given:
        executer.withArgument('-Dorg.gradle.internal.property-provenance=false')
        buildFile << """
            def value = objects.property(String)
            value.get()
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('Cannot query the value of this property because it has no value available.')
        !failure.error.contains('Configuration of')
        !failure.error.contains('Configuration of')
    }
}
