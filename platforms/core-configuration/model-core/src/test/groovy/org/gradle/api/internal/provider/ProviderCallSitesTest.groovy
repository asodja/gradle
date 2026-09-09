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
import org.gradle.api.internal.provenance.EffectiveProvenanceView
import org.gradle.api.internal.provenance.ProvenanceCheckpoint
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.api.internal.provenance.SourceLocation
import spock.lang.Specification

class ProviderCallSitesTest extends Specification {
    def scope = new ScopeIdentity(':', ':')
    def caller = new Attribution(new ContributorKey('build', ContributorKey.Kind.PLUGIN_ID, 'source'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'source', 'source'), scope, 'application')
    def host = Stub(PropertyProvenanceHost) {
        getOwnerScope() >> scope
        newOccurrenceScope() >> 'original'
        currentAttribution() >> { caller }
    }
    def factory = new DefaultPropertyFactory(host)

    def 'declaration locations are captured without evaluation and survive checkpoints'() {
        given:
        int evaluations = 0
        def value = factory.property(String)
        value.set(new DefaultProvider({ evaluations++; 'value' }))
        def accepted = value.lastAcceptedMutation

        when:
        def mapped = ProviderCallSites.map(value, { evaluations++; it }, new SourceLocation('Mapping.kt', 11))
        caller = new Attribution(caller.contributor, new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'filter', 'filter'), scope, 'filter')
        def filtered = ProviderCallSites.filter(mapped, { evaluations++; false }, new SourceLocation('Filtering.java', 23))
        def checkpoint = filtered.provenanceCheckpoint
        def restored = ProvenanceCheckpoint.decode(checkpoint.encode())
        def operations = restored.view.providerOperations
        def report = PropertyProvenanceRenderer.configuration(restored.view)

        then:
        evaluations == 0
        value.lastAcceptedMutation.is(accepted)
        operations*.kind == [EffectiveProvenanceView.ProviderBoundary.MAP, EffectiveProvenanceView.ProviderBoundary.FILTER]
        operations*.attribution*.location == [new SourceLocation('Mapping.kt', 11), new SourceLocation('Filtering.java', 23)]
        operations*.attribution*.origin*.identifier == ['source', 'filter']
        report.indexOf("filter by plugin 'filter' (Filtering.java:23)") < report.indexOf("map by plugin 'source' (Mapping.kt:11)")
        report.contains('set by ')
        !report.contains('selection unknown')
        restored.encode() == checkpoint.encode()

        when:
        filtered.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains("filter by plugin 'filter' (Filtering.java:23)")
        evaluations == 3
    }

    def 'captures #operation while retaining ordinary results and honest coverage notes'() {
        given:
        def value = factory.property(String)
        value.set('left')
        def map = factory.mapProperty(String, String)
        map.set([key: 'value'])
        def location = new SourceLocation('Plugin.java', 10)

        when:
        def derived
        switch (operation) {
            case 'map': derived = ProviderCallSites.map(value, { it + '!' }, location); break
            case 'filter': derived = ProviderCallSites.filter(value, { true }, location); break
            case 'flatMap': derived = ProviderCallSites.flatMap(value, { Providers.of(it + '!') }, location); break
            case 'orElseValue': derived = ProviderCallSites.orElse(value, 'fallback', location); break
            case 'orElseProvider': derived = ProviderCallSites.orElse(value, Providers.of('fallback'), location); break
            case 'zip': derived = ProviderCallSites.zip(value, Providers.of('right'), { left, right -> left + right }, location); break
            case 'factoryZip': derived = ProviderCallSites.zip(new DefaultProviderFactory(), value, Providers.of('right'), { left, right -> left + right }, location); break
            case 'getting': derived = ProviderCallSites.getting(map, 'key', location); break
            case 'keySet': derived = ProviderCallSites.keySet(map, location); break
        }

        then:
        derived.get() == expected
        derived.effectiveProvenance.providerOperations.last().attribution.location == location
        derived.configurationTrace.contains(note)
        derived.configurationTrace.contains('selection unknown') == branching

        where:
        operation        | expected    | branching | note
        'map'            | 'left!'     | false     | 'set by '
        'filter'         | 'left'      | false     | 'set by '
        'flatMap'        | 'left!'     | true      | 'selection unknown'
        'orElseValue'    | 'left'      | true      | 'selection unknown'
        'orElseProvider' | 'left'      | true      | 'selection unknown'
        'zip'            | 'leftright' | false     | 'left input shown; other input not traced'
        'factoryZip'     | 'leftright' | false     | 'left input shown; other input not traced'
        'getting'        | 'value'     | false     | 'set by '
        'keySet'         | ['key'] as Set | false  | 'set by '
    }

    def 'disabled receivers stay ordinary and declaration does not validate deferred callbacks'() {
        given:
        def ordinary = Providers.of('value')
        def location = new SourceLocation('Plugin.java', 4)

        expect:
        !(ProviderCallSites.map(ordinary, { it }, location) instanceof ProvenanceAware)
        PropertySourceLocations.capture(ordinary, 'Plugin.java', 4) == null

        when:
        ProviderCallSites.map(factory.property(String), null, location)

        then:
        noExceptionThrown()
    }
}
