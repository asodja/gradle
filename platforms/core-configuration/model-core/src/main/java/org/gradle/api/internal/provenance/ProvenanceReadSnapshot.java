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

package org.gradle.api.internal.provenance;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable descriptor references captured before a read, with view assembly deferred until needed. */
public abstract class ProvenanceReadSnapshot {
    public abstract EffectiveProvenanceView toView();

    public static ProvenanceReadSnapshot captured(
        ScopeIdentity owner, String modelPath, EffectiveProvenanceView.Source source,
        UpdateSequence updates, @Nullable MutationOccurrence convention
    ) {
        return new Captured(owner, modelPath, source, updates, convention);
    }

    public static ProvenanceReadSnapshot fromView(EffectiveProvenanceView view) {
        return new ExistingView(view);
    }

    public final ProvenanceReadSnapshot through(List<EffectiveProvenanceView.ProviderBoundary> boundaries) {
        return new Derived(this, boundaries);
    }

    private static final class Captured extends ProvenanceReadSnapshot {
        private final ScopeIdentity owner;
        private final String modelPath;
        private final EffectiveProvenanceView.Source source;
        private final UpdateSequence updates;
        @Nullable
        private final MutationOccurrence convention;

        private Captured(ScopeIdentity owner, String modelPath, EffectiveProvenanceView.Source source, UpdateSequence updates, @Nullable MutationOccurrence convention) {
            this.owner = owner;
            this.modelPath = modelPath;
            this.source = source;
            this.updates = updates;
            this.convention = convention;
        }

        @Override
        public EffectiveProvenanceView toView() {
            return EffectiveProvenanceView.captured(new TargetContext(owner, modelPath), source, updates, convention);
        }
    }

    private static final class ExistingView extends ProvenanceReadSnapshot {
        private final EffectiveProvenanceView view;

        private ExistingView(EffectiveProvenanceView view) {
            this.view = view;
        }

        @Override
        public EffectiveProvenanceView toView() {
            return view;
        }
    }

    private static final class Derived extends ProvenanceReadSnapshot {
        private final ProvenanceReadSnapshot input;
        private final List<EffectiveProvenanceView.ProviderBoundary> boundaries;

        private Derived(ProvenanceReadSnapshot input, List<EffectiveProvenanceView.ProviderBoundary> boundaries) {
            this.input = input;
            this.boundaries = Collections.unmodifiableList(new ArrayList<>(boundaries));
        }

        @Override
        public EffectiveProvenanceView toView() {
            return input.toView().through(boundaries);
        }
    }
}
