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
import org.gradle.api.internal.provenance.ProvenanceRenderer;
import org.gradle.api.internal.provenance.OrdinaryProvenanceState;
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.api.internal.provenance.TargetContext;
import org.gradle.api.internal.provenance.UpdateSequence;
import org.gradle.api.internal.provenance.MutationOccurrence;
import org.jspecify.annotations.Nullable;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import java.util.Collection;
import java.util.Map;

/** Captured collection supplier and descriptor view; further source rebinding does not alter either. */
public abstract class CollectionProvenanceSnapshot<C> extends AbstractMinimalProvider<C> {
    private final Class<C> type;
    private final ScopeIdentity owner;
    private final String modelPath;
    private final EffectiveProvenanceView.Source source;
    private final UpdateSequence updates;
    @Nullable
    private final MutationOccurrence convention;

    private CollectionProvenanceSnapshot(Class<C> type, OrdinaryProvenanceState state, String modelPath) {
        this.type = type;
        this.owner = state.getOwnerScope();
        this.modelPath = state.getModelPath(modelPath);
        this.source = state.getSource();
        this.updates = state.getUpdates();
        this.convention = state.getConvention();
    }

    static <T, C extends Collection<T>> CollectionProvenanceSnapshot<C> collection(Class<C> type, CollectionSupplier<T, C> supplier, OrdinaryProvenanceState state, String modelPath) {
        return new CollectionSnapshot<>(type, supplier, state, modelPath);
    }

    static <K, V> CollectionProvenanceSnapshot<Map<K, V>> map(Class<Map<K, V>> type, MapSupplier<K, V> supplier, OrdinaryProvenanceState state, String modelPath) {
        return new MapSnapshot<>(type, supplier, state, modelPath);
    }

    public EffectiveProvenanceView getEffectiveProvenance() {
        return EffectiveProvenanceView.captured(new TargetContext(owner, modelPath), source, updates, convention);
    }

    public String getConfigurationTrace() {
        return ProvenanceRenderer.configuration(getEffectiveProvenance());
    }

    @Override
    public Class<C> getType() {
        return type;
    }

    @Override
    protected Value<? extends C> calculateOwnPresentValue() {
        try {
            return super.calculateOwnPresentValue();
        } catch (MissingValueException failure) {
            throw PropertyProvenanceDiagnostics.missing(failure, getEffectiveProvenance());
        }
    }

    private static final class CollectionSnapshot<T, C extends Collection<T>> extends CollectionProvenanceSnapshot<C> {
        private final CollectionSupplier<T, C> supplier;

        private CollectionSnapshot(Class<C> type, CollectionSupplier<T, C> supplier, OrdinaryProvenanceState state, String modelPath) {
            super(type, state, modelPath);
            this.supplier = supplier;
        }

        @Override
        public ValueProducer getProducer() {
            try (EvaluationScopeContext ignored = openScope()) {
                return supplier.getProducer();
            }
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            try (EvaluationScopeContext ignored = openScope()) {
                return supplier.calculateExecutionTimeValue();
            }
        }

        @Override
        protected Value<? extends C> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext ignored = openScope()) {
                return supplier.calculateValue(consumer);
            }
        }
    }

    private static final class MapSnapshot<K, V> extends CollectionProvenanceSnapshot<Map<K, V>> {
        private final MapSupplier<K, V> supplier;

        private MapSnapshot(Class<Map<K, V>> type, MapSupplier<K, V> supplier, OrdinaryProvenanceState state, String modelPath) {
            super(type, state, modelPath);
            this.supplier = supplier;
        }

        @Override
        public ValueProducer getProducer() {
            try (EvaluationScopeContext ignored = openScope()) {
                return supplier.getProducer();
            }
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue() {
            try (EvaluationScopeContext ignored = openScope()) {
                return supplier.calculateExecutionTimeValue();
            }
        }

        @Override
        protected Value<? extends Map<K, V>> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext ignored = openScope()) {
                return supplier.calculateValue(consumer);
            }
        }
    }
}
