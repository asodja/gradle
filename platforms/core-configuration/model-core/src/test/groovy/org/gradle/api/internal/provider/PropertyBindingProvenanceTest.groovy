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
import org.gradle.internal.Describables
import spock.lang.Specification

class PropertyBindingProvenanceTest extends Specification {
    def scope = new ScopeIdentity(':', ':')
    def caller = new Attribution(new ContributorKey('build', ContributorKey.Kind.PLUGIN_ID, 'demo'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'demo', 'demo'), scope, 'application')
    def factory = new DefaultPropertyFactory(Stub(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> { UUID.randomUUID().toString() }
        currentAttribution() >> { caller }
    })

    def 'named missing value follows a live sanitized provider chain without diagnostic evaluation'() {
        given:
        def source = factory.property(String)
        def target = factory.property(String)
        target.attachOwner(null, Describables.of("task ':check' property 'value'"))
        PropertyCallSites.set(source, 'first', new SourceLocation('Source.java', 10))
        int calls = 0
        def mapped = ProviderCallSites.map(source, { calls++; it }, new SourceLocation('Map.java', 20))
        def chain = ProviderCallSites.filter(mapped, { calls++; false }, new SourceLocation('Filter.java', 30))
        PropertyCallSites.set(target, chain, new SourceLocation('Target.java', 40))
        PropertyCallSites.set(source, 'latest', new SourceLocation('Source.java', 11))

        when:
        def trace = target.configurationTrace
        def bytes = PropertyProvenanceTransport.encodeCheckpoint(target)

        then:
        calls == 0
        trace.indexOf('Target.java:40') < trace.indexOf('Filter.java:30')
        trace.indexOf('Filter.java:30') < trace.indexOf('Map.java:20')
        trace.indexOf('Map.java:20') < trace.indexOf('Source.java:11')
        !trace.contains('Source.java:10')
        PropertyProvenanceRenderer.configuration(ProvenanceCheckpoint.decode(bytes).view) == trace

        when:
        target.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.startsWith("Cannot query the value of task ':check' property 'value'")
        failure.message.contains(trace)
        calls == 2
    }

    def 'engine selection replacement clearing and finalization determine the traversed binding'() {
        given:
        def convention = factory.property(String)
        def explicit = factory.property(String)
        PropertyCallSites.set(convention, 'convention', new SourceLocation('Convention.java', 1))
        PropertyCallSites.set(explicit, 'explicit', new SourceLocation('Explicit.java', 2))
        def target = factory.property(String)
        target.convention(convention)
        target.set(explicit.map { it })

        expect:
        target.configurationTrace.contains('Explicit.java:2')
        !target.configurationTrace.contains('Convention.java:1')

        when:
        target.unset()

        then:
        target.configurationTrace.contains('Convention.java:1')
        !target.configurationTrace.contains('Explicit.java:2')

        when:
        target.finalizeValue()
        def frozen = target.configurationTrace
        PropertyCallSites.set(convention, 'later', new SourceLocation('Later.java', 3))

        then:
        target.get() == 'convention'
        target.configurationTrace == frozen
        target.shallowCopy().configurationTrace == frozen

        when:
        target.set(explicit)

        then:
        thrown(IllegalStateException)
        target.configurationTrace == frozen
    }

    def 'restored historical input is retained until a new binding replaces it'() {
        given:
        def source = factory.property(String)
        PropertyCallSites.set(source, 'old', new SourceLocation('Source.java', 1))
        def target = factory.property(String)
        target.set(source.map { it })
        def expected = target.configurationTrace
        def checkpoint = PropertyProvenanceTransport.encodeCheckpoint(target)
        def restored = factory.property(String)

        when:
        PropertyProvenanceTransport.restore(restored, checkpoint, { restored.set('old') })
        source.set('new')

        then:
        restored.configurationTrace == expected

        when:
        restored.set('replacement')

        then:
        !restored.configurationTrace.contains('Source.java:1')
        restored.effectiveProvenance.input == null
    }

    def 'cycles and deep bindings are bounded without evaluating any provider'() {
        given:
        def first = factory.property(String)
        def second = factory.property(String)
        first.set(second.map { throw new AssertionError('evaluated') })
        second.set(first)

        expect:
        first.configurationTrace.contains('Provider binding cycle')

        when:
        def head = factory.property(String)
        head.set(new DefaultProvider({ throw new AssertionError('evaluated') }))
        100.times {
            def next = factory.property(String)
            next.set(head)
            head = next
        }

        then:
        head.configurationTrace.contains('Provider binding depth limit')
        PropertyProvenanceRenderer.configuration(ProvenanceCheckpoint.decode(PropertyProvenanceTransport.encodeCheckpoint(head)).view) == head.configurationTrace

        when:
        first.set('ordinary')

        then:
        !first.configurationTrace.contains('cycle')
        first.get() == 'ordinary'
    }

    def 'shallow copies keep their captured binding while its upstream configuration remains live'() {
        given:
        def source = factory.property(String)
        PropertyCallSites.set(source, 'old', new SourceLocation('Old.java', 1))
        def target = factory.property(String)
        target.set(source.map { it })
        def copy = target.shallowCopy()

        when:
        target.set('replacement')
        PropertyCallSites.set(source, 'latest', new SourceLocation('Latest.java', 2))

        then:
        copy.get() == 'latest'
        copy.configurationTrace.contains('Latest.java:2')
        !copy.configurationTrace.contains('Old.java:1')
        target.effectiveProvenance.input == null
    }

    def 'rebinding transported inputs keeps descriptor history bounded'() {
        given:
        def current = factory.property(String)
        current.set('value')

        when:
        80.times {
            def transported = PropertyProvenanceTransport.provider(Providers.of('value'), PropertyProvenanceTransport.encodeCheckpoint(current))
            def next = factory.property(String)
            next.set(transported)
            current = next
        }
        def view = ProvenanceCheckpoint.decode(PropertyProvenanceTransport.encodeCheckpoint(current)).view
        int depth = 0
        while (view.input != null) {
            depth++
            view = view.input
        }

        then:
        depth == 32
        current.configurationTrace.contains('Provider binding depth limit')
        current.get() == 'value'
    }

    def 'bound and upstream overridden conventions remain with their property sections'() {
        given:
        def source = factory.property(String)
        source.convention('source fallback')
        source.set('source')
        def target = factory.property(String)
        target.convention('target fallback')
        target.set(source.filter { false })

        expect:
        target.configurationTrace.contains('        Overridden:')
        target.configurationTrace.contains('\n    Overridden:')
        target.configurationTrace.count('Overridden:') == 2
        target.configurationTrace.count('convention by') == 2
    }

    def 'binding arrows identify direct and derived inputs without repeating the same task'() {
        given:
        def source = factory.property(String)
        def target = factory.property(String)
        source.attachOwner(null, Describables.of(sourceName))
        target.attachOwner(null, Describables.of("task ':check' property 'value'"))
        source.set('source')
        target.set(derived ? source.map { it } : source)

        expect:
        target.configurationTrace.contains("    → ${arrow}\n        ")

        where:
        sourceName                                | derived | arrow
        "task ':check' property 'source'"          | true    | "provider derived from property 'source'"
        "task ':check' property 'source'"          | false   | "property 'source'"
        "task ':other' property 'source'"          | true    | "provider derived from task ':other' property 'source'"
        "'unnamed property'"                       | false   | 'an unnamed property'
    }

    def 'upstream descriptors precede mutations performed during evaluation'() {
        given:
        def source = factory.property(String)
        def target = factory.property(String)
        PropertyCallSites.set(source, 'before', new SourceLocation('Before.java', 1))
        target.set(source.filter {
            PropertyCallSites.set(source, 'after', new SourceLocation('After.java', 2))
            false
        })

        when:
        target.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains('Before.java:1')
        !failure.message.contains('After.java:2')
        target.configurationTrace.contains('After.java:2')
    }
}
