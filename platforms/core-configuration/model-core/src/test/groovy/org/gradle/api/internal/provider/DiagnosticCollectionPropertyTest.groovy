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

package org.gradle.api.internal.provider

import org.gradle.api.internal.provenance.Attribution
import org.gradle.api.internal.provenance.ContributorKey
import org.gradle.api.internal.provenance.DiagnosticOrigin
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.api.internal.provenance.SemanticOperation
import org.gradle.internal.state.ModelObject
import org.gradle.internal.Describables
import spock.lang.Specification

class DiagnosticCollectionPropertyTest extends Specification {
    def scope = new ScopeIdentity('build', ':project')
    int nextProperty
    int captures
    def attribution = author('source')
    def host = Stub(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> { "domain/property/${nextProperty++}".toString() }
        currentAttribution() >> { captures++; attribution }
    }
    def factory = new DefaultPropertyFactory(host)

    def 'factory enables only project collection properties'() {
        expect:
        factory.listProperty(String) instanceof DiagnosticListProperty
        factory.setProperty(String) instanceof DiagnosticSetProperty
        factory.mapProperty(String, String) instanceof DiagnosticMapProperty
        new DefaultPropertyFactory(PropertyHost.NO_OP).listProperty(String).class == DefaultListProperty
        new DefaultPropertyFactory(PropertyHost.NO_OP).setProperty(String).class == DefaultSetProperty
        new DefaultPropertyFactory(PropertyHost.NO_OP).mapProperty(String, String).class == DefaultMapProperty
    }

    def 'contribution selects ordinary default or captured convention for #kind with preserving=#preserving'() {
        given:
        def property = create(kind)
        property.convention(values(kind, 'base'))
        def convention = property.lastAcceptedMutation
        attribution = author('contributor')

        when:
        contribute(property, kind, 'added', preserving)

        then:
        property.get() == values(kind, *(preserving ? ['base', 'added'] : ['added']))
        property.effectiveProvenance.updates.size() == 1
        property.lastAcceptedMutation.sequence == 1
        property.lastAcceptedMutation.operation.kind == SemanticOperation.Kind.CONTRIBUTION
        property.effectiveProvenance.source.occurrence == (preserving ? convention : null)
        property.effectiveProvenance.shadowedConfiguration == (preserving ? [] : [convention])
        captures == 2
        property.configurationTrace.contains("contribution ${kind == 'map' ? (preserving ? 'insert' : 'put') : (preserving ? 'append' : 'add')}")

        when:
        property.convention(values(kind, 'later'))

        then:
        property.get() == values(kind, *(preserving ? ['base', 'added'] : ['added']))
        property.effectiveProvenance.updates.size() == 1

        where:
        [kind, preserving] << [['list', 'set', 'map'], [false, true]].combinations()
    }

    def 'explicit missing remains selected and absent contributions are not evaluated for #kind'() {
        given:
        def property = create(kind)
        property.convention(values(kind, 'secret'))
        property.set(Providers.notDefined())
        def source = property.lastAcceptedMutation
        contribute(property, kind, 'added', true)

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.cause.class == MissingValueException
        failure.message.contains("Failure trace to source for 'unnamed property'")
        failure.message.contains('explicit source')
        !failure.message.contains('secret')
        property.effectiveProvenance.source.occurrence.is(source)
        property.effectiveProvenance.updates.size() == 1
        property.effectiveProvenance.shadowedConfiguration.size() == 1

        where:
        kind << ['list', 'set', 'map']
    }

    def 'null reset absorbs contributions until explicit empty for #kind'() {
        given:
        def property = create(kind)
        property.setFromAnyValue(null)

        when:
        contribute(property, kind, 'ignored', false)

        then:
        !property.present
        property.lastAcceptedMutation.operation.kind == SemanticOperation.Kind.CONTRIBUTION
        property.effectiveProvenance.updates.size() == 0
        property.configurationTrace.contains('default missing collection')

        when:
        property.empty()
        contribute(property, kind, 'retained', false)

        then:
        property.get() == values(kind, 'retained')
        property.effectiveProvenance.updates.size() == 1

        where:
        kind << ['list', 'set', 'map']
    }

    def 'copy and finalization preserve named provenance and live upstream #kind'() {
        given:
        def upstream = create(kind)
        upstream.set(values(kind, 'first'))
        def property = create(kind)
        property.attachOwner(null, Describables.of('extension.items'))
        property.set(upstream)
        contribute(property, kind, 'local', false)
        def copy = property.shallowCopy()
        def source = copy.effectiveProvenance.source.occurrence
        def contributions = copy.effectiveProvenance.updates

        when:
        upstream.set(values(kind, 'second'))
        property.set(values(kind, 'replacement'))

        then:
        copy.get() == values(kind, 'second', 'local')
        copy.configurationTrace.contains('for extension.items')
        copy.effectiveProvenance.source.occurrence.is(source)
        copy.effectiveProvenance.updates.is(contributions)
        property.effectiveProvenance.updates.size() == 0

        when:
        property.set(upstream)
        contribute(property, kind, 'fixed', false)
        def checkpoint = property.effectiveProvenance
        property.finalizeValue()
        upstream.set(values(kind, 'third'))

        then:
        property.get() == values(kind, 'second', 'fixed')
        property.effectiveProvenance.source.occurrence.is(checkpoint.source.occurrence)
        property.effectiveProvenance.updates.is(checkpoint.updates)
        property.shallowCopy().get() == property.get()

        where:
        kind << ['list', 'set', 'map']
    }

    def 'explanation and snapshots never query dependency values or recapture origins #kind'() {
        given:
        def supplier = new DefaultProvider({ throw new AssertionError('must not evaluate') })
        def property = create(kind)
        property.set(supplier)
        if (kind == 'map') {
            property.put('key', supplier)
        } else {
            property.add(supplier)
        }
        def count = captures

        expect:
        property.configurationTrace.contains('contribution')
        property.shallowCopy().configurationTrace == property.configurationTrace
        captures == count

        where:
        kind << ['list', 'set', 'map']
    }

    def 'replace map retains captured contributions then rebinding discards them #kind'() {
        given:
        def property = create(kind)
        property.set(values(kind, 'base'))
        contribute(property, kind, 'local', false)
        def source = property.effectiveProvenance.source.occurrence
        property.replace { it.map { it } }

        expect:
        property.get() == values(kind, 'base', 'local')
        property.effectiveProvenance.source.occurrence.is(source)
        property.effectiveProvenance.updates.inApplicationOrder()*.operation*.kind == [SemanticOperation.Kind.CONTRIBUTION, SemanticOperation.Kind.UPDATE]

        when:
        property.replace { Providers.of(values(kind, 'replacement')) }

        then:
        property.get() == values(kind, 'replacement')
        !property.effectiveProvenance.completeLocal
        property.effectiveProvenance.updates.size() == 0

        where:
        kind << ['list', 'set', 'map']
    }

    def 'rejected mutations preserve accepted occurrence and original cause #kind #operation'() {
        given:
        def property = create(kind)
        property.set(values(kind, 'accepted'))
        property.finalizeValue()
        def occurrence = property.lastAcceptedMutation

        when:
        mutate(property, kind, operation)

        then:
        def failure = thrown(IllegalStateException)
        failure.cause.message == 'The value for this property is final and cannot be changed any further.'
        failure.message.contains('Failure trace to source')
        failure.message.contains('unknown caller origin')
        property.lastAcceptedMutation.is(occurrence)
        property.get() == values(kind, 'accepted')

        where:
        [kind, operation] << [['list', 'set', 'map'], ['set', 'convention', 'unset', 'unsetConvention', 'empty', 'add', 'append', 'replace']].combinations()
    }

    def 'rejected preserving bulk contribution reports its operation #kind #input'() {
        given:
        def property = create(kind)
        property.set(values(kind, 'accepted'))
        property.finalizeValue()

        when:
        if (kind == 'map') {
            if (input == 'provider') {
                property.insertAll(Providers.of([key: 'value']))
            } else {
                property.insertAll([key: 'value'])
            }
        } else if (input == 'provider') {
            property.appendAll(Providers.of(['one', 'two']))
        } else if (input == 'varargs') {
            property.appendAll('one', 'two')
        } else {
            property.appendAll(['one', 'two'])
        }

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('failed ' + (kind == 'map' ? 'insertAll' : 'appendAll'))
        failure.cause.message == 'The value for this property is final and cannot be changed any further.'

        where:
        kind   | input
        'list' | 'iterable'
        'list' | 'provider'
        'list' | 'varargs'
        'set'  | 'iterable'
        'set'  | 'provider'
        'set'  | 'varargs'
        'map'  | 'map'
        'map'  | 'provider'
    }

    def 'bulk contribution is one occurrence including duplicates and overwritten keys #kind'() {
        given:
        def property = create(kind)
        property.set(values(kind, 'same'))

        when:
        if (kind == 'map') {
            property.putAll([same: 'overwritten', extra: 'extra'])
        } else {
            property.addAll(['same', 'extra'])
        }

        then:
        property.lastAcceptedMutation.sequence == 1
        property.effectiveProvenance.updates.size() == 1
        property.lastAcceptedMutation.operation.shapes == [kind == 'map' ? SemanticOperation.Shape.PUT_ALL : SemanticOperation.Shape.ADD_ALL]
        property.get() == (kind == 'map' ? [same: 'overwritten', extra: 'extra'] : values(kind, 'same', 'same', 'extra'))

        where:
        kind << ['list', 'set', 'map']
    }

    def 'varargs contributions use one accepted boundary with preserving=#preserving for #kind'() {
        given:
        def property = create(kind)
        property.convention(['base'])

        when:
        if (preserving) {
            property.appendAll('one', 'two')
        } else {
            property.addAll('one', 'two')
        }

        then:
        property.lastAcceptedMutation.sequence == 1
        property.effectiveProvenance.updates.size() == 1
        property.lastAcceptedMutation.operation.shapes == [preserving ? SemanticOperation.Shape.APPEND_ALL : SemanticOperation.Shape.ADD_ALL]
        property.get() == values(kind, *(preserving ? ['base', 'one', 'two'] : ['one', 'two']))

        where:
        [kind, preserving] << [['list', 'set'], [false, true]].combinations()
    }

    def 'finalize on read freezes contributions without retaining upstream changes #kind'() {
        given:
        def upstream = create(kind)
        upstream.set(values(kind, 'first'))
        def property = create(kind)
        property.set(upstream)
        contribute(property, kind, 'local', false)
        property.finalizeValueOnRead()
        def accepted = property.lastAcceptedMutation

        expect:
        property.get() == values(kind, 'first', 'local')

        when:
        upstream.set(values(kind, 'later'))

        then:
        property.get() == values(kind, 'first', 'local')
        property.lastAcceptedMutation.is(accepted)
        property.effectiveProvenance.updates.size() == 1

        where:
        kind << ['list', 'set', 'map']
    }

    def 'ordinary sequence agrees with disabled engine after every operation #kind'() {
        given:
        def tracked = create(kind)
        def plain = create(kind, new DefaultPropertyFactory(PropertyHost.NO_OP))
        def operations = ['convention', 'add', 'convention', 'unset', 'append', 'set', 'add', 'unsetConvention', 'unset', 'null', 'add', 'empty', 'append']

        expect:
        operations.every { operation ->
            mutate(tracked, kind, operation)
            mutate(plain, kind, operation)
            tracked.getOrNull() == plain.getOrNull()
        }

        where:
        kind << ['list', 'set', 'map']
    }

    def 'null replace rejection names outer operation and does not accept a mutation #kind'() {
        given:
        def property = create(kind)
        property.set(values(kind, 'accepted'))
        property.disallowChanges()
        def accepted = property.lastAcceptedMutation
        attribution = author('rejected')

        when:
        property.replace { null }

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains("failed replace [plugin 'rejected'")
        property.lastAcceptedMutation.is(accepted)

        where:
        kind << ['list', 'set', 'map']
    }

    def 'failed finalization leaves diagnostic state mutable for #kind'() {
        given:
        def property = create(kind)
        property.set(new DefaultProvider({ throw new IllegalStateException('calculation failed') }))
        def accepted = property.lastAcceptedMutation

        when:
        property.finalizeValue()

        then:
        thrown(IllegalStateException)
        !property.finalized
        property.lastAcceptedMutation.is(accepted)

        when:
        attribution = author('recovery')
        property.set(values(kind, 'recovered'))
        property.finalizeValue()

        then:
        property.get() == values(kind, 'recovered')
        property.lastAcceptedMutation.sequence == accepted.sequence + 1
        property.configurationTrace.contains("plugin 'recovery'")

        where:
        kind << ['list', 'set', 'map']
    }

    def 'nested replacement restores diagnostic operation after callback failure for #kind'() {
        given:
        def property = create(kind)
        property.set(values(kind, 'original'))

        when:
        property.replace { previous ->
            property.replace { it.map { it } }
            throw new IllegalStateException('outer callback failed')
        }

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('failed replace')
        property.lastAcceptedMutation.sequence == 1
        property.effectiveProvenance.updates.size() == 1

        when:
        property.set(values(kind, 'recovered'))
        contribute(property, kind, 'added', false)

        then:
        property.get() == values(kind, 'recovered', 'added')
        property.lastAcceptedMutation.sequence == 3
        property.lastAcceptedMutation.operation.kind == SemanticOperation.Kind.CONTRIBUTION
        property.effectiveProvenance.source.occurrence.sequence == 2
        property.effectiveProvenance.updates.size() == 1

        where:
        kind << ['list', 'set', 'map']
    }

    private static PropertyProvenanceHost enabledHost(PropertyHost readHost) {
        def scope = new ScopeIdentity('build', ':project')
        def attribution = new Attribution(new ContributorKey('domain', ContributorKey.Kind.UNKNOWN, 'unknown'),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, 'unknown', 'unknown'), scope, null)
        new PropertyProvenanceHost() {
            @Override
            ScopeIdentity getOwnerScope() { scope }

            @Override
            String newOccurrenceScope() { UUID.randomUUID().toString() }

            @Override
            Attribution currentAttribution() { attribution }

            @Override
            String beforeRead(ModelObject producer) { readHost.beforeRead(producer) }
        }
    }

    static class ListCircularEvaluationTest extends DefaultListPropertyTest.ListPropertyCircularFunctionEvaluationTest {
        @Override
        DefaultListProperty<String> property() { new DiagnosticListProperty<String>(enabledHost(host), String) }
    }

    static class SetCircularEvaluationTest extends DefaultSetPropertyTest.SetPropertyCircularFunctionEvaluationTest {
        @Override
        DefaultSetProperty<String> property() { new DiagnosticSetProperty<String>(enabledHost(host), String) }
    }

    static class MapCircularEvaluationTest extends MapPropertySpec.MapPropertyCircularChainEvaluationTest {
        @Override
        DefaultMapProperty<String, String> property() { new DiagnosticMapProperty<String, String>(enabledHost(host), String, String) }
    }

    private Attribution author(String name) {
        new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, name),
            new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, name, name), scope, name)
    }

    private def create(String kind, DefaultPropertyFactory selected = factory) {
        switch (kind) {
            case 'list': return selected.listProperty(String)
            case 'set': return selected.setProperty(String)
            default: return selected.mapProperty(String, String)
        }
    }

    private static def values(String kind, String... entries) {
        kind == 'map' ? entries.collectEntries { [(it): it] } : (kind == 'set' ? entries.toList().toSet() : entries.toList())
    }

    private static void contribute(def property, String kind, String value, boolean preserving) {
        if (kind == 'map') {
            if (preserving) { property.insert(value, value) } else { property.put(value, value) }
        } else {
            if (preserving) { property.append(value) } else { property.add(value) }
        }
    }

    private static void mutate(def property, String kind, String operation) {
        switch (operation) {
            case 'set': property.set(values(kind, 'set')); break
            case 'convention': property.convention(values(kind, 'convention')); break
            case 'null': property.setFromAnyValue(null); break
            case 'add': contribute(property, kind, 'added', false); break
            case 'append': contribute(property, kind, 'appended', true); break
            case 'replace': property.replace { it.map { it } }; break
            default: property."$operation"()
        }
    }
}
