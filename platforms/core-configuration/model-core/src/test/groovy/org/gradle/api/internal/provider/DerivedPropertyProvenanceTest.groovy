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
import org.gradle.internal.Describables
import org.gradle.internal.hash.ClassLoaderHierarchyHasher
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

class DerivedPropertyProvenanceTest extends Specification {
    def scope = new ScopeIdentity('build', ':project')
    int captures
    def attribution = new Attribution(new ContributorKey('build', ContributorKey.Kind.PLUGIN_ID, 'source'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'source', 'source'), scope, 'application')
    def host = Stub(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> 'original'
        currentAttribution() >> { captures++; attribution }
    }
    def factory = new DefaultPropertyFactory(host)
    def property = factory.property(String)

    def 'missing derived #operation reports boundary after isolation=#isolated'() {
        given:
        property.set(Providers.notDefined())
        def original = derived(operation, property)
        def before = captures
        def expected = original.configurationTrace

        when:
        def provider = isolated ? roundtrip(original) : original
        provider.get()

        then:
        def failure = thrown(MissingValueException)
        !failure.message.contains('Provider transformations:')
        failure.message.contains(operation in ['flatMap', 'orElse'] ? 'selection unknown' : operation == 'zip' ? 'left input shown; other input not traced' : ' by ')
        failure.message.contains("plugin 'source'")
        provider.configurationTrace == expected
        captures == before
        provider.orNull == null
        !provider.present
        provider.getOrElse('fallback') == 'fallback'

        where:
        [operation, isolated] << [['map', 'filter', 'flatMap', 'orElse', 'zip'], [false, true]].combinations()
    }

    def 'explanation never evaluates transforms or unused branches'() {
        given:
        property.set('left')
        int evaluations
        def fallback = new DefaultProvider({ evaluations++; throw new AssertionError('unused fallback') })
        def provider = property.map { evaluations++; it }.orElse(fallback).flatMap { evaluations++; Providers.of(it) }
        def before = captures

        expect:
        provider.configurationTrace.contains('flatMap')
        evaluations == 0
        provider.get() == 'left'
        evaluations == 2
        captures == before
    }

    def 'derived views follow live source while snapshots preserve captured configuration'() {
        given:
        property.set('old')
        def live = property.map { it }
        def copy = property.shallowCopy().map { it }
        def oldOccurrence = copy.effectiveProvenance.source.occurrence

        when:
        property.set('new')

        then:
        live.get() == 'new'
        copy.get() == 'old'
        live.effectiveProvenance.source.occurrence != oldOccurrence
        copy.effectiveProvenance.source.occurrence == oldOccurrence
    }

    def 'map entry missing with map missing=#missing reports context without exposing key'() {
        given:
        def map = factory.mapProperty(String, String)
        map.set(missing ? Providers.notDefined() : Providers.of([:]))
        def entry = map.getting('secret-key')

        when:
        entry.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains('absent key or an absent map')
        !failure.message.contains('secret-key')
        map.keySet().configurationTrace.contains('mapKeys')

        where:
        missing << [false, true]
    }

    def 'evaluation failure keeps identity and pre-evaluation source for #query'() {
        given:
        property.set('original')
        def before = property.lastAcceptedMutation
        def original = new UnsupportedOperationException('transform failed')
        def provider = property.map {
            property.set('mutated')
            throw original
        }

        when:
        query.call(provider)

        then:
        def failure = thrown(UnsupportedOperationException)
        failure.is(original)
        failure.suppressed.size() == 1
        failure.suppressed[0].message.contains('map by')
        failure.suppressed[0].stackTrace.length == 0
        property.lastAcceptedMutation != before

        where:
        query << [{ it.get() }, { it.orNull }, { it.present }, { it.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead) }, { it.calculateExecutionTimeValue() }]
    }

    def 'failure diagnostics use source descriptors from before a reentrant mutation'() {
        given:
        property.attachOwner(null, Describables.of('before'))
        property.set(new DefaultProvider({
            property.attachOwner(null, Describables.of('after'))
            throw new UnsupportedOperationException('failed')
        }))

        when:
        property.get()

        then:
        def failure = thrown(RuntimeException)
        failure.suppressed[0].message.contains('of before ')
        !failure.suppressed[0].message.contains('of after ')
    }

    def 'failed finalization restores ordinary mutability and retains original exception'() {
        given:
        def original = new UnsupportedOperationException('supplier failed')
        property.set(new DefaultProvider({ throw original }))
        def checkpoint = property.lastAcceptedMutation

        when:
        property.finalizeValue()

        then:
        def failure = thrown(UnsupportedOperationException)
        failure.is(original)
        failure.suppressed.size() == 1
        property.lastAcceptedMutation == checkpoint

        when:
        property.set('recovered')

        then:
        property.get() == 'recovered'
    }

    def 'early validation of #kind #operation records no accepted mutation'() {
        given:
        def target = kind == 'map' ? factory.mapProperty(String, String) : factory.listProperty(String)
        def before = target.lastAcceptedMutation
        def action = operation == 'dynamic' ? { target.setFromAnyValue(42) } :
            kind == 'map' ? { target.put(null, 'value') } : { target.add((String) null) }

        when:
        action()

        then:
        def failure = thrown(RuntimeException)
        failure.message.contains('Configuration of')
        failure.cause.class == (operation == 'dynamic' ? IllegalArgumentException : NullPointerException)
        target.lastAcceptedMutation == before

        where:
        [kind, operation] << [['list', 'map'], ['dynamic', 'null']].combinations()
    }

    def 'nested append validation reports outer operation exactly once'() {
        given:
        def list = factory.listProperty(String)

        when:
        list.append((String) null)

        then:
        def failure = thrown(NullPointerException)
        failure.message.contains('failed append')
        failure.message.count('Configuration of') == 1
    }

    def 'plain providers stay undecorated'() {
        given:
        def plain = new DefaultPropertyFactory(PropertyHost.NO_OP).property(String)

        when:
        plain.map { it }.get()

        then:
        def failure = thrown(MissingValueException)
        !failure.message.contains('Configuration of')
        !(plain.map { it } instanceof ProvenanceAware)
    }


    def 'evaluation failures retain identity for #kind query=#query'() {
        given:
        def target = kind == 'scalar' ? factory.property(String) :
            kind == 'list' ? factory.listProperty(String) :
            kind == 'set' ? factory.setProperty(String) : factory.mapProperty(String, String)
        def original = new UnsupportedOperationException('original failure')
        target.set(new DefaultProvider({ throw original }))
        def before = captures

        when:
        query.call(target)

        then:
        def failure = thrown(UnsupportedOperationException)
        failure.is(original)
        failure.suppressed.size() == 1
        captures == before

        where:
        [kind, query] << [['scalar', 'list', 'set', 'map'],
            [{ it.get() }, { it.orNull }, { it.present }, { it.finalizeValue() },
             { it.calculateExecutionTimeValue() }, { it.calculateValue(ValueSupplier.ValueConsumer.IgnoreUnsafeRead) }]].combinations()
    }

    def 'unsafe read keeps the engine exception and adds context'() {
        given:
        def readHost = Stub(PropertyProvenanceHost) {
            getOwnerScope() >> scope
            newOccurrenceScope() >> 'read'
            currentAttribution() >> attribution
            beforeRead(_) >> 'owner is not ready'
        }
        def target = new DefaultPropertyFactory(readHost).property(String)
        target.set('value')
        target.disallowUnsafeRead()

        when:
        target.get()

        then:
        def failure = thrown(IllegalStateException)
        failure.class == IllegalStateException
        failure.message.contains('owner is not ready')
        failure.suppressed.size() == 1
        failure.suppressed[0].message.contains("plugin 'source'")
    }

    def 'diagnostic lookup failure does not mask evaluation failure or rerun the action'() {
        given:
        def source = Stub(ProvenanceAware) {
            getProvenanceReadSnapshot() >> { throw new IllegalStateException('metadata unavailable') }
        }
        def original = new UnsupportedOperationException('value failure')
        int evaluations

        when:
        PropertyProvenanceDiagnostics.evaluate(source, { evaluations++; throw original })

        then:
        def failure = thrown(UnsupportedOperationException)
        failure.is(original)
        evaluations == 1
    }

    def 'derivation preserves side effects and branch liveness'() {
        given:
        int effects
        property.set(Providers.of('value').withSideEffect({ effects++ } as ValueSupplier.SideEffect))
        def provider = property.map { it }.filter { true }

        expect:
        provider.configurationTrace.contains('filter')
        effects == 0
        provider.get() == 'value'
        effects == 1
        provider.get() == 'value'
        effects == 2

        when:
        property.set(Providers.notDefined())
        def fallback = property.orElse(Providers.of('fallback'))

        then:
        fallback.get() == 'fallback'
        fallback.configurationTrace.contains('selection unknown')
        !fallback.effectiveProvenance.completeLocal
    }


    def 'configured input can produce a missing derived #operation'() {
        given:
        property.set('configured')
        def provider = operation == 'map' ? property.map { null } :
            operation == 'filter' ? property.filter { false } :
            operation == 'flatMap' ? property.flatMap { Providers.notDefined() } :
            property.zip(Providers.of('right')) { left, right -> null }

        when:
        provider.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains(operation == 'flatMap' ? 'selection unknown' : operation == 'zip' ? 'left input shown; other input not traced' : ' by ')
        failure.message.contains("plugin 'source'")
        property.get() == 'configured'
        provider.orNull == null

        where:
        operation << ['map', 'filter', 'flatMap', 'zip']
    }

    def 'deferred side effect failures keep their identity for #query'() {
        given:
        def original = new UnsupportedOperationException('side effect failure')
        property.set(Providers.of('value').withSideEffect({ throw original } as ValueSupplier.SideEffect))
        def provider = property.map { it }

        when:
        query.call(provider)

        then:
        def failure = thrown(UnsupportedOperationException)
        failure.is(original)
        failure.suppressed.size() == 1
        failure.suppressed[0].message.contains('map by')

        where:
        query << [{ it.get() }, { it.orNull }, { it.getOrElse('fallback') }]
    }


    def 'reading mapped task content before producer completion retains the rejection'() {
        given:
        def taskState = Stub(org.gradle.api.tasks.TaskState) { getExecuted() >> false }
        def task = Stub(org.gradle.api.Task) {
            getState() >> taskState
            toString() >> "task ':producer'"
        }
        def upstream = Stub(ProviderInternal) {
            getType() >> String
            getProducer() >> ValueSupplier.ValueProducer.task(task)
        }
        property.set(upstream)

        when:
        property.map { it }.get()

        then:
        def failure = thrown(org.gradle.api.InvalidUserCodeException)
        failure.class == org.gradle.api.InvalidUserCodeException
        failure.message.contains("before task ':producer' has completed")
        failure.suppressed.size() == 1
        failure.suppressed[0].message.contains('map by')
    }

    def 'arbitrary replacement callback failure reports attempted operation without an accepted mutation'() {
        given:
        property.set('original')
        def before = property.lastAcceptedMutation
        def original = new UnsupportedOperationException('callback failed')

        when:
        property.replace { throw original }

        then:
        def failure = thrown(UnsupportedOperationException)
        failure.is(original)
        failure.suppressed.size() == 1
        failure.suppressed[0].message.contains('failed replace')
        property.lastAcceptedMutation == before
        property.get() == 'original'
    }


    def 'a broken custom cause accessor cannot replace the original evaluation failure'() {
        given:
        def original = new UnsupportedOperationException('original failure') {
            @Override
            Throwable getCause() { throw new IllegalStateException('broken cause accessor') }
        }

        expect:
        PropertyProvenanceDiagnostics.evaluation(original, property.effectiveProvenance).is(original)
    }


    def 'long derived chains can be explained and transported without recursive descriptor traversal'() {
        given:
        property.set(new DefaultProvider({ throw new AssertionError('supplier evaluated') }))
        def provider = property
        4096.times { provider = provider.map { it } }

        when:
        def trace = provider.configurationTrace
        def checkpoint = org.gradle.api.internal.provenance.ProvenanceCheckpoint.decode(PropertyProvenanceTransport.encodeCheckpoint(provider))

        then:
        trace.contains('additional provider transformations omitted')
        checkpoint.view.providerBoundaries.size() == 4096
        checkpoint.view.source.occurrence == property.lastAcceptedMutation
    }


    def 'read snapshot preserves input context when presence evaluation mutates the source'() {
        given:
        property.attachOwner(null, Describables.of('before'))
        property.set(new DefaultProvider({
            property.attachOwner(null, Describables.of('after'))
            property.set('replacement')
            return null
        }))
        def accepted = property.lastAcceptedMutation
        def provider = property.map { it }
        def snapshot = PropertyProvenanceDiagnostics.snapshot(provider)

        when:
        def present = provider.present
        def view = snapshot.toView()

        then:
        !present
        view.target.modelPath == 'before'
        view.source.occurrence.is(accepted)
        view.providerBoundaries == [org.gradle.api.internal.provenance.EffectiveProvenanceView.ProviderBoundary.MAP]
        property.get() == 'replacement'
        property.lastAcceptedMutation != accepted
    }

    def 'successful evaluation does not materialize the read snapshot'() {
        given:
        def snapshot = Mock(org.gradle.api.internal.provenance.ProvenanceReadSnapshot)
        def source = Stub(ProvenanceAware) {
            getProvenanceReadSnapshot() >> snapshot
        }

        when:
        def value = PropertyProvenanceDiagnostics.evaluate(source, { 'value' })

        then:
        value == 'value'
        0 * snapshot._
    }

    def 'managed isolation preserves a named property binding and its missing derived input'() {
        given:
        property.set('source')
        def bound = factory.property(String)
        bound.attachOwner(null, Describables.of("task ':check' property 'value'"))
        bound.set(property.map { it }.filter { false })
        def expected = bound.configurationTrace

        when:
        def restored = roundtrip(bound)
        restored.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.startsWith("Cannot query the value of task ':check' property 'value'")
        failure.message.contains(expected)
        restored.configurationTrace == expected
        restored.effectiveProvenance.input.providerBoundaries*.name() == ['MAP', 'FILTER']
    }

    private static def derived(String operation, def source) {
        switch (operation) {
            case 'map': return source.map { it }
            case 'filter': return source.filter { true }
            case 'flatMap': return source.flatMap { Providers.of(it) }
            case 'orElse': return source.orElse(Providers.notDefined())
            case 'zip': return source.zip(Providers.of('right')) { left, right -> left + right }
        }
    }

    private def roundtrip(def value) {
        def registry = TestUtil.managedFactoryRegistry()
        def hasher = Stub(ClassLoaderHierarchyHasher) {
            getClassLoaderHash(_) >> TestHashCodes.hashCodeFrom(123)
        }
        def isolator = new DefaultIsolatableFactory(hasher, registry)
        def serializer = IsolatableSerializerRegistry.create(hasher, registry)
        def bytes = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(bytes)
        serializer.writeIsolatable(encoder, isolator.isolate(value))
        encoder.flush()
        serializer.readIsolatable(new KryoBackedDecoder(new ByteArrayInputStream(bytes.toByteArray()))).isolate()
    }
}
