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
import org.gradle.api.internal.provenance.MutationOccurrence
import org.gradle.api.internal.provenance.SemanticOperation
import org.gradle.api.internal.provenance.ProvenanceCheckpoint
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

class PropertyProvenanceTransportTest extends Specification {
    def scope = new ScopeIdentity('build', ':project')
    int captures
    int namespace
    def author = new Attribution(new ContributorKey('build', ContributorKey.Kind.PLUGIN_ID, 'source'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'source', 'source'), scope, 'application')
    def host = Stub(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> { "original/${namespace++}".toString() }
        currentAttribution() >> { captures++; author }
    }
    def factory = new DefaultPropertyFactory(host)
    def registry = TestUtil.managedFactoryRegistry()
    def hasher = Stub(ClassLoaderHierarchyHasher) {
        getClassLoaderHash(_) >> TestHashCodes.hashCodeFrom(123)
    }
    def isolator = new DefaultIsolatableFactory(hasher, registry)
    def serializer = IsolatableSerializerRegistry.create(hasher, registry)

    def 'isolation and serialized isolation preserve provenance for #kind missing=#missing snapshot=#snapshot'() {
        given:
        def property = create(kind)
        property.attachOwner(null, Describables.of('named property'))
        property.convention(value(kind, 'convention'))
        property.set(missing ? Providers.notDefined() : Providers.of(value(kind, 'value')))
        if (kind == 'map') {
            property.put('contribution', 'entry')
        } else if (kind != 'scalar') {
            property.add('entry')
        }
        property.replace { previous -> previous.map { it } }
        def original = snapshot ? property.shallowCopy() : property
        def before = PropertyProvenanceTransport.encodeCheckpoint(original)
        def capturesBefore = captures

        when:
        def isolated = isolator.isolate(original)
        def recreated = roundtrip(isolated).isolate()
        def copy = isolated.isolate()

        then:
        recreated.orNull == original.orNull
        copy.orNull == original.orNull
        PropertyProvenanceTransport.encodeCheckpoint(recreated) == before
        PropertyProvenanceTransport.encodeCheckpoint(copy) == before
        recreated.configurationTrace == original.configurationTrace
        captures == capturesBefore

        where:
        [kind, missing, snapshot] << [['scalar', 'list', 'set', 'map'], [false, true], [false, true]].combinations()
    }

    def 'managed scalar recreation permits inferred collection type #kind'() {
        given:
        def original = factory.property(Object)
        original.set(value(kind, 'entry'))
        def checkpoint = PropertyProvenanceTransport.encodeCheckpoint(original)

        when:
        def isolated = isolator.isolate(original)
        def copies = [isolated.isolate(), roundtrip(isolated).isolate()]

        then:
        copies.every {
            it instanceof org.gradle.api.provider.Property &&
                it.get() == original.get() &&
                PropertyProvenanceTransport.encodeCheckpoint(it) == checkpoint
        }

        where:
        kind << ['list', 'set', 'map']
    }

    def 'recreated branches preserve old IDs and allocate independent new IDs for #kind'() {
        given:
        def original = create(kind)
        original.set(value(kind, 'source'))
        def isolated = isolator.isolate(original)
        def first = isolated.isolate()
        def second = isolated.isolate()
        def accepted = original.lastAcceptedMutation

        expect:
        first.lastAcceptedMutation == accepted
        second.lastAcceptedMutation == accepted

        when:
        first.set(value(kind, 'first'))
        second.set(value(kind, 'second'))

        then:
        first.lastAcceptedMutation != second.lastAcceptedMutation
        first.lastAcceptedMutation.sequence == 0
        second.lastAcceptedMutation.sequence == 0
        first.lastAcceptedMutation != accepted
        second.lastAcceptedMutation != accepted
        first.effectiveProvenance.source.occurrence == first.lastAcceptedMutation
        first.effectiveProvenance.source.occurrence.attribution.contributor.kind == ContributorKey.Kind.UNKNOWN
        original.get() == value(kind, 'source')

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'recreation retains historical convention explanation without resurrecting a live convention for #kind'() {
        given:
        def original = create(kind)
        original.convention(value(kind, 'convention'))
        def restored = isolator.isolate(original).isolate()

        expect:
        restored.configurationTrace == original.configurationTrace

        when:
        restored.unset()

        then:
        !restored.configurationTrace.contains("plugin 'source'")
        restored.orNull == (kind == 'scalar' ? null : value(kind, null))

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'isolated missing collection retains ordinary default behavior for #kind'() {
        given:
        def tracked = create(kind)
        def plain = create(kind, new DefaultPropertyFactory(PropertyHost.NO_OP))
        tracked.set(Providers.notDefined())
        plain.set(Providers.notDefined())
        def trackedCopy = isolator.isolate(tracked).isolate()
        def plainCopy = isolator.isolate(plain).isolate()

        when:
        trackedCopy.unset()
        plainCopy.unset()

        then:
        trackedCopy.orNull == plainCopy.orNull
        !trackedCopy.present

        where:
        kind << ['list', 'set', 'map']
    }

    def 'unnamed missing properties retain the original failure message after isolation for #kind'() {
        given:
        def original = create(kind)
        original.set(Providers.notDefined())
        def restored = isolator.isolate(original).isolate()

        when:
        original.get()

        then:
        def originalFailure = thrown(MissingValueException)

        when:
        restored.get()

        then:
        def restoredFailure = thrown(MissingValueException)
        restoredFailure.message == originalFailure.message

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'provenance does not change isolated value fingerprints for #kind'() {
        given:
        def tracked = create(kind)
        def plain = create(kind, new DefaultPropertyFactory(PropertyHost.NO_OP))
        tracked.set(value(kind, 'same'))
        plain.set(value(kind, 'same'))

        expect:
        isolator.isolate(tracked).asSnapshot() == isolator.isolate(plain).asSnapshot()

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'isolation captures the plan provenance before supplier evaluation for #kind'() {
        given:
        def original = create(kind)
        original.set(new DefaultProvider({
            original.set(value(kind, 'later'))
            value(kind, 'captured')
        }))
        def checkpoint = PropertyProvenanceTransport.encodeCheckpoint(original)

        when:
        def restored = isolator.isolate(original).isolate()

        then:
        restored.get() == value(kind, 'captured')
        original.get() == value(kind, 'later')
        PropertyProvenanceTransport.encodeCheckpoint(restored) == checkpoint

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'failed restoration resumes mutation tracking for #kind'() {
        given:
        def property = create(kind)
        property.set(value(kind, 'original'))
        def checkpoint = PropertyProvenanceTransport.encodeCheckpoint(property)
        def failure = new IllegalStateException('restore failed')
        def before = captures

        when:
        PropertyProvenanceTransport.restore(property, checkpoint, {
            property.set(value(kind, 'restored'))
            throw failure
        })

        then:
        def caught = thrown(IllegalStateException)
        caught.is(failure)
        captures == before
        PropertyProvenanceTransport.encodeCheckpoint(property) == checkpoint

        when:
        property.set(value(kind, 'next'))

        then:
        captures == before + 1
        property.get() == value(kind, 'next')
        PropertyProvenanceTransport.encodeCheckpoint(property) != checkpoint

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'restoration without a checkpoint still restores the value'() {
        given:
        def property = new DefaultPropertyFactory(PropertyHost.NO_OP).property(String)

        when:
        PropertyProvenanceTransport.restore(property, null, { property.set('restored') })

        then:
        property.get() == 'restored'
        PropertyProvenanceTransport.encodeCheckpoint(property) == null
    }

    def 'checkpoint encoding and decoding never evaluate suppliers'() {
        given:
        def property = factory.property(String)
        property.set(new DefaultProvider<String>({ throw new AssertionError('supplier evaluated') }))

        when:
        def bytes = PropertyProvenanceTransport.encodeCheckpoint(property)
        def restored = ProvenanceCheckpoint.decode(bytes)

        then:
        restored.lastAcceptedMutation == property.lastAcceptedMutation
        restored.view.target.owner == scope
        restored.view.source.occurrence.attribution == author
    }

    def 'checkpoint retains long labels and all #count contributions beyond renderer limits'() {
        given:
        def property = factory.listProperty(String)
        property.attachOwner(null, Describables.of('x' * 70000))
        count.times { property.add('value') }

        when:
        def restored = ProvenanceCheckpoint.decode(PropertyProvenanceTransport.encodeCheckpoint(property))

        then:
        restored.view.target.modelPath.size() == 70000
        restored.view.updates.inApplicationOrder() == property.effectiveProvenance.updates.inApplicationOrder()
        restored.lastAcceptedMutation == property.lastAcceptedMutation

        where:
        count << [0, 1, 128, 4096]
    }

    def 'checkpoint round trip preserves semantic operation #operation.kind #operation.shapes'() {
        given:
        def occurrence = new MutationOccurrence('original', 42, author, operation)
        def checkpoint = new ProvenanceCheckpoint(factory.property(String).effectiveProvenance, occurrence)

        when:
        def restored = ProvenanceCheckpoint.decode(checkpoint.encode()).lastAcceptedMutation

        then:
        restored == occurrence
        restored.attribution == author
        restored.operation.kind == operation.kind
        restored.operation.shapes == operation.shapes
        restored.operation.reason == operation.reason

        where:
        operation << [SemanticOperation.EXPLICIT_BINDING, SemanticOperation.CONVENTION_BINDING,
                      SemanticOperation.CLEAR_EXPLICIT, SemanticOperation.CLEAR_CONVENTION,
                      SemanticOperation.PROMOTE_CONVENTION, SemanticOperation.unclassifiedBinding('opaque'),
                      SemanticOperation.update(SemanticOperation.Shape.MAP, SemanticOperation.Shape.FLAT_MAP, SemanticOperation.Shape.ZIP)] +
            SemanticOperation.Shape.values().collect { SemanticOperation.contribution(it) }
    }

    def 'unknown checkpoint versions fail explicitly'() {
        given:
        def bytes = PropertyProvenanceTransport.encodeCheckpoint(factory.property(String))
        bytes[3] = 99

        when:
        ProvenanceCheckpoint.decode(bytes)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message == 'Unsupported property provenance checkpoint version.'
    }

    private def roundtrip(def isolated) {
        def bytes = new ByteArrayOutputStream()
        def encoder = new KryoBackedEncoder(bytes)
        serializer.writeIsolatable(encoder, isolated)
        encoder.flush()
        serializer.readIsolatable(new KryoBackedDecoder(new ByteArrayInputStream(bytes.toByteArray())))
    }

    private def create(String kind, def source = factory) {
        switch (kind) {
            case 'scalar': return source.property(String)
            case 'list': return source.listProperty(String)
            case 'set': return source.setProperty(String)
            case 'map': return source.mapProperty(String, String)
        }
    }

    private static def value(String kind, String text) {
        switch (kind) {
            case 'scalar': return text
            case 'list': return text == null ? [] : [text]
            case 'set': return text == null ? [] as Set : [text] as Set
            case 'map': return text == null ? [:] : [key: text]
        }
    }
}
