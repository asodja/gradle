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

package org.gradle.api.internal.provenance

import spock.lang.Specification

class ProvenanceReadSnapshotTest extends Specification {
    def ownerScope = new ScopeIdentity('build', ':project')
    def attribution = new Attribution(new ContributorKey('domain', ContributorKey.Kind.PLUGIN_ID, 'source'),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, 'source', 'source'), ownerScope, 'application')
    def state = new OrdinaryProvenanceState(ownerScope, 'property')

    def 'snapshot retains accepted descriptors and target name across later changes'() {
        given:
        state.acceptedConvention(attribution, false)
        def convention = state.lastAcceptedMutation
        state.acceptedBinding(attribution, SemanticOperation.EXPLICIT_BINDING)
        state.acceptedContribution(attribution, SemanticOperation.contribution(SemanticOperation.Shape.PUT), true)
        def source = state.source
        def updates = state.updates
        def snapshot = state.readSnapshot('before')

        when:
        state.acceptedBinding(attribution, SemanticOperation.EXPLICIT_BINDING)
        state.acceptedClearConvention(attribution, true)
        def view = snapshot.toView()

        then:
        view.target.modelPath == 'before'
        view.target.owner.is(ownerScope)
        view.source.is(source)
        view.updates.is(updates)
        view.shadowedConfiguration == [convention]
    }

    def 'snapshot preserves complete existing #mode checkpoint without rebuilding it'() {
        given:
        state.acceptedBinding(attribution, SemanticOperation.unclassifiedBinding('opaque provider'))
        def existing = state.getEffectiveProvenance('recorded').through([EffectiveProvenanceView.ProviderBoundary.ZIP])
        if (mode == 'finalized') {
            state.freeze(existing)
        } else {
            state.restore(new ProvenanceCheckpoint(existing, state.lastAcceptedMutation))
        }

        when:
        def snapshot = state.readSnapshot('renamed')

        then:
        snapshot.toView().is(existing)
        snapshot.toView().partialReasons == ['opaque provider']
        snapshot.toView().providerBoundaries == [EffectiveProvenanceView.ProviderBoundary.ZIP]

        where:
        mode << ['finalized', 'restored']
    }

    def 'derived snapshot owns its boundary list'() {
        given:
        def boundaries = [EffectiveProvenanceView.ProviderBoundary.MAP]
        def snapshot = state.readSnapshot('original').through(boundaries)

        when:
        boundaries.clear()
        state.acceptedBinding(attribution, SemanticOperation.EXPLICIT_BINDING)

        then:
        snapshot.toView().providerBoundaries == [EffectiveProvenanceView.ProviderBoundary.MAP]
        snapshot.toView().source.knowledge == EffectiveProvenanceView.SourceKnowledge.UNCONFIGURED
    }
}
