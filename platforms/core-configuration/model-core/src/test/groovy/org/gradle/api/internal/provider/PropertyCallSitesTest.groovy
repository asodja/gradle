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
import org.gradle.api.internal.provenance.ProvenanceCheckpoint
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.api.internal.provenance.SourceLocation
import spock.lang.Specification

class PropertyCallSitesTest extends Specification {
    def scope = new ScopeIdentity('build', ':project')
    def attribution = new Attribution(new ContributorKey('build', ContributorKey.Kind.PLUGIN_ID, 'source'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'source', 'source'), scope, 'application')
    def host = Stub(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> 'original'
        currentAttribution() >> attribution
    }
    def factory = new DefaultPropertyFactory(host)

    def 'locations belong to individual mutations and survive checkpoints for #kind'() {
        given:
        def property = create(kind)

        when:
        PropertyCallSites.convention(property, value(kind), new SourceLocation('Plugin.java', 11))
        PropertyCallSites.set(property, value(kind), new SourceLocation('build.gradle.kts', 27))
        def view = property.effectiveProvenance
        def copy = ProvenanceCheckpoint.decode(new ProvenanceCheckpoint(view, null).encode()).view

        then:
        view.source.occurrence.attribution.location == new SourceLocation('build.gradle.kts', 27)
        copy.source.occurrence.attribution == view.source.occurrence.attribution
        new ProvenanceCheckpoint(copy, null).encode() == new ProvenanceCheckpoint(view, null).encode()
        property.configurationTrace.contains('(build.gradle.kts:27)')
        attribution.location == null
        view.source.occurrence.attribution.contributor == attribution.contributor

        when:
        property.unset()

        then:
        property.effectiveProvenance.source.occurrence.attribution.location == new SourceLocation('Plugin.java', 11)

        when:
        property.set(value(kind))

        then:
        property.effectiveProvenance.source.occurrence.attribution.location == null

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'failed calls retain their location and clear the scope for #kind'() {
        given:
        def property = create(kind)
        PropertyCallSites.set(property, value(kind), new SourceLocation('Plugin.java', 10))
        property.finalizeValue()

        when:
        PropertyCallSites.set(property, value(kind), new SourceLocation('Plugin.java', 20))

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('Plugin.java:20')
        failure.message.contains('Plugin.java:10')

        when:
        def next = create(kind)
        next.set(value(kind))

        then:
        next.effectiveProvenance.source.occurrence.attribution.location == null

        where:
        kind << ['scalar', 'list', 'set', 'map']
    }

    def 'replace callbacks do not borrow the calling location and nested calls restore it'() {
        given:
        def property = factory.property(String)
        def other = factory.property(String)
        property.set('before')

        when:
        PropertyCallSites.replace(property, {
            property.set('nested')
            assert property.effectiveProvenance.source.occurrence.attribution.location == null
            PropertyCallSites.set(other, 'other', new SourceLocation('Other.kt', 8))
            assert other.effectiveProvenance.source.occurrence.attribution.location == new SourceLocation('Other.kt', 8)
            Providers.of('after')
        }, new SourceLocation('Plugin.java', 50))

        then:
        property.get() == 'after'
        property.configurationTrace.contains('Plugin.java:50')
        PropertyCallSites.attribution(attribution, property).location == null
    }

    def 'location capture does not evaluate providers and ordinary properties remain ordinary'() {
        given:
        int calls = 0
        def provider = new DefaultProvider({ calls++; 'value' })
        def property = factory.property(String)
        def ordinary = new DefaultPropertyFactory(PropertyHost.NO_OP).property(String)

        when:
        PropertyCallSites.set(property, provider, new SourceLocation('Plugin.java', 10))
        PropertyCallSites.set(ordinary, provider, new SourceLocation('Plugin.java', 20))

        then:
        calls == 0
        !(ordinary instanceof ProvenanceAware)
        PropertyCallSites.attribution(attribution, ordinary).location == null
    }

    def 'nested scopes are receiver-specific and do not cross threads'() {
        given:
        def first = factory.property(String)
        def second = factory.property(String)
        def observed = new java.util.concurrent.atomic.AtomicReference<Attribution>()

        when:
        PropertyCallSites.withLocation(first, new SourceLocation('First.java', 1), {
            assert PropertyCallSites.attribution(attribution, second).location == null
            def thread = Thread.start {
                observed.set(PropertyCallSites.attribution(attribution, first))
            }
            thread.join()
            try {
                PropertyCallSites.withLocation(second, new SourceLocation('Second.kt', 2), {
                    assert PropertyCallSites.attribution(attribution, second).location == new SourceLocation('Second.kt', 2)
                    throw new IllegalStateException('nested failure')
                })
            } catch (IllegalStateException expected) {
                assert expected.message == 'nested failure'
            }
            assert PropertyCallSites.attribution(attribution, first).location == new SourceLocation('First.java', 1)
            null
        })

        then:
        observed.get().location == null
        PropertyCallSites.attribution(attribution, first).location == null
        PropertyCallSites.attribution(attribution, second).location == null
    }

    def 'source labels are sanitized and bounded when rendered'() {
        given:
        def property = factory.property(String)
        PropertyCallSites.set(property, 'value', new SourceLocation('Unsafe\n\u001b' + ('x' * 1000) + '.java', 9))

        expect:
        !property.configurationTrace.contains('Unsafe\n')
        !property.configurationTrace.contains('\u001b')
        !property.configurationTrace.contains('x' * 1000)
        property.configurationTrace.contains(':9')
    }

    private def create(String kind) {
        switch (kind) {
            case 'scalar': return factory.property(String)
            case 'list': return factory.listProperty(String)
            case 'set': return factory.setProperty(String)
            case 'map': return factory.mapProperty(String, String)
            default: throw new IllegalArgumentException(kind)
        }
    }

    private static def value(String kind) {
        kind == 'scalar' ? 'value' : kind == 'map' ? [key: 'value'] : ['value']
    }
}
