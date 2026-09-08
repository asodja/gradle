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

package org.gradle.api.internal.provider;

import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.ProvenanceReadSnapshot;
import org.gradle.api.internal.provenance.MutationOccurrence;
import org.gradle.api.internal.provenance.ProvenanceRenderer;
import org.gradle.api.internal.provenance.ProvenanceCheckpoint;
import org.gradle.internal.Describables;
import org.jspecify.annotations.Nullable;

/** Read-only explanation surface implemented by the enabled collection property subclasses. */
public interface CollectionPropertyDiagnostics extends RestorableProvenance {
    CollectionPropertyProvenance getProvenance();

    @Override
    default void beginProvenanceRestore() {
        getProvenance().restoring = true;
    }

    @Override
    default void restoreProvenance(ProvenanceCheckpoint checkpoint) {
        getProvenance().state.restore(checkpoint);
        String modelPath = checkpoint.getView().getTarget().getModelPath();
        if (!modelPath.equals("'unnamed property'")) {
            ((AbstractProperty<?, ?>) this).attachOwner(null, Describables.of(modelPath));
        }
        getProvenance().restoring = false;
    }

    @Override
    default ProvenanceReadSnapshot getProvenanceReadSnapshot() {
        return getProvenance().state.readSnapshot(CollectionPropertyProvenance.modelPath(((AbstractProperty<?, ?>) this).getDeclaredDisplayName()));
    }

    @Override
    default EffectiveProvenanceView getEffectiveProvenance() {
        return getProvenance().state.getEffectiveProvenance(CollectionPropertyProvenance.modelPath(((AbstractProperty<?, ?>) this).getDeclaredDisplayName()));
    }

    @Override
    @Nullable
    default MutationOccurrence getLastAcceptedMutation() {
        return getProvenance().state.getLastAcceptedMutation();
    }

    @Override
    default String getConfigurationTrace() {
        return ProvenanceRenderer.configuration(getEffectiveProvenance());
    }
}
