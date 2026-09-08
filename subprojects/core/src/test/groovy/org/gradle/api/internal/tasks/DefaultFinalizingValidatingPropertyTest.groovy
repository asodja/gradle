/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks

import org.gradle.api.internal.tasks.properties.DefaultFinalizingValidatingProperty
import org.gradle.api.internal.tasks.properties.PropertyValidationContext
import org.gradle.api.internal.provider.ProvenanceAware
import org.gradle.api.internal.provenance.ProvenanceReadSnapshot
import org.gradle.api.provider.Provider
import org.gradle.api.internal.tasks.properties.LifecycleAwareValue
import org.gradle.api.internal.tasks.properties.ValidationActions
import org.gradle.internal.properties.PropertyValue
import spock.lang.Specification

class DefaultFinalizingValidatingPropertyTest extends Specification {
    def "notifies property value of start and end of execution when it implements lifecycle interface"() {
        def value = Mock(LifecycleAwareValue)
        def valueWrapper = Stub(PropertyValue)
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, false, ValidationActions.NO_OP)

        given:
        valueWrapper.call() >> value

        when:
        property.prepareValue()

        then:
        1 * value.prepareValue()
        0 * value._

        when:
        property.cleanupValue()

        then:
        1 * value.cleanupValue()
        0 * value._
    }

    def "does not notify null property value"() {
        def valueWrapper = Stub(PropertyValue)
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, true, ValidationActions.NO_OP)

        given:
        valueWrapper.call() >> null

        when:
        property.prepareValue()
        property.cleanupValue()

        then:
        noExceptionThrown()
    }

    def "does not notify property value that does not implement lifecycle interface"() {
        def valueWrapper = Stub(PropertyValue)
        def property = new DefaultFinalizingValidatingProperty("name", valueWrapper, false, ValidationActions.NO_OP)

        given:
        valueWrapper.call() >> "thing"

        when:
        property.prepareValue()
        property.cleanupValue()

        then:
        noExceptionThrown()
    }
    def 'validation only captures required properties and materializes missing values optional=#optional present=#present'() {
        given:
        def provider = Mock(TrackedProvider)
        def snapshot = Mock(ProvenanceReadSnapshot)
        def valueWrapper = Stub(PropertyValue) { call() >> provider }
        def context = Mock(PropertyValidationContext)
        def target = new DefaultFinalizingValidatingProperty('name', valueWrapper, optional, ValidationActions.NO_OP)

        when:
        target.validate(context)

        then:
        (optional ? 0 : 1) * provider.getProvenanceReadSnapshot() >> snapshot

        then:
        1 * provider.isPresent() >> present

        then:
        (!optional && !present ? 1 : 0) * snapshot.toView()
        (!optional && !present ? 1 : 0) * context.visitPropertyError(_)
        0 * provider._
        0 * snapshot._

        where:
        [optional, present] << [[false, true], [false, true]].combinations()
    }

    interface TrackedProvider extends Provider<String>, ProvenanceAware {}

}
