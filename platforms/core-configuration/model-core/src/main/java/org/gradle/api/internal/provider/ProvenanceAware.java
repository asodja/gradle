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
import org.gradle.api.internal.provenance.MutationOccurrence;
import org.gradle.api.internal.provenance.ProvenanceCheckpoint;
import org.gradle.api.internal.provenance.ProvenanceReadSnapshot;
import org.jspecify.annotations.Nullable;

/** Read-only descriptor transport surface for diagnostic properties and captured providers. */
public interface ProvenanceAware {
    EffectiveProvenanceView getEffectiveProvenance();

    /** Captures current immutable descriptors, never a callback into mutable property state. */
    default ProvenanceReadSnapshot getProvenanceReadSnapshot() {
        return ProvenanceReadSnapshot.fromView(getEffectiveProvenance());
    }

    default String getConfigurationTrace() {
        return PropertyProvenanceRenderer.configuration(getEffectiveProvenance());
    }

    @Nullable
    default MutationOccurrence getLastAcceptedMutation() {
        return null;
    }

    default ProvenanceCheckpoint getProvenanceCheckpoint() {
        return new ProvenanceCheckpoint(getEffectiveProvenance(), getLastAcceptedMutation());
    }
}
