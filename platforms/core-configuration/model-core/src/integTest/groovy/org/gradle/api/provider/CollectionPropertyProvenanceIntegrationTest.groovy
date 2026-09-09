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

import static org.hamcrest.Matchers.containsString

@UnsupportedWithConfigurationCache(because = 'Scalar and collection provenance transport is D3')
class CollectionPropertyProvenanceIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        executer.withArgument('-Dorg.gradle.internal.property-provenance=true')
    }

    def 'managed #kind reports named source and repeated plugin contributions after #lifecycle'() {
        given:
        buildFile << """
            abstract class Model {
                abstract ${type} getItems()
            }
            class SourcePlugin implements Plugin<Project> {
                void apply(Project project) {
                    def model = project.extensions.create('model', Model)
                    model.items.convention(${value})
                    model.items.set(project.providers.provider { null })
                }
            }
            class ContributionPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.model.items.${contribution}
                    project.model.items.${contribution}
                }
            }
            apply plugin: SourcePlugin
            apply plugin: ContributionPlugin
            def target = model.items
            ${prepare}
            println target.configurationTrace
            target.get()
        """

        when:
        fails('help')

        then:
        output.contains("Configuration of extension 'model' property 'items'")
        output.count("${operation} by plugin 'ContributionPlugin'") == 2
        failure.assertThatCause(containsString("Configuration of extension 'model' property 'items'"))
        failure.assertThatCause(containsString("set by plugin 'SourcePlugin'"))
        failure.assertThatCause(containsString('Overridden'))
        !failure.error.contains('secret')

        where:
        kind   | type                          | value              | contribution                  | operation | lifecycle        | prepare
        'list' | 'ListProperty<String>'        | "['secret']"       | "add('entry')"                | 'add'     | 'mutable'        | ''
        'set'  | 'SetProperty<String>'         | "['secret']"       | "add('entry')"                | 'add'     | 'copy'           | 'target = target.shallowCopy()'
        'map'  | 'MapProperty<String, String>' | "[key: 'secret']"  | "put('key', 'entry')"          | 'put'     | 'finalized copy' | 'target.finalizeValue(); target = target.shallowCopy()'
    }

    def 'settings configured project collections preserve source origins'() {
        given:
        settingsFile << """
            gradle.beforeProject { project ->
                def items = project.objects.listProperty(String)
                project.extensions.add('items', items)
                items.set(project.providers.provider { null })
            }
        """
        buildFile << """
            items.add('secret')
            items.finalizeValue()
            items.shallowCopy().get()
        """

        when:
        fails('help')

        then:
        failure.assertThatCause(containsString('set by settings file'))
        failure.assertThatCause(containsString("scope 'settings'"))
        failure.assertThatCause(containsString('add by build file'))
    }

    def 'disabled collection failure retains original message for #creation'() {
        given:
        executer.withArgument('-Dorg.gradle.internal.property-provenance=false')
        buildFile << """
            def items = objects.${creation}
            items.set(providers.provider { null })
            items.get()
        """

        when:
        fails('help')

        then:
        failure.assertHasCause('Cannot query the value of this property because it has no value available.')
        !failure.error.contains('trace to source')

        where:
        creation << ['listProperty(String)', 'setProperty(String)', 'mapProperty(String, String)']
    }
}
