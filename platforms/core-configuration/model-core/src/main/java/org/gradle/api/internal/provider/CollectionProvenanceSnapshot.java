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

import org.gradle.api.Transformer;
import org.gradle.api.internal.provenance.EffectiveProvenanceView.ProviderBoundary;
import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.ProvenanceReadSnapshot;
import org.gradle.api.internal.provenance.MutationOccurrence;
import org.gradle.api.internal.provenance.OrdinaryProvenanceState;
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.api.internal.provenance.TargetContext;
import org.gradle.api.internal.provenance.UpdateSequence;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiFunction;

/** Captured collection supplier and descriptor view; further source rebinding does not alter either. */
public abstract class CollectionProvenanceSnapshot<C> extends AbstractMinimalProvider<C> implements ProvenanceAware {
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

    @Override
    public ProvenanceReadSnapshot getProvenanceReadSnapshot() {
        return ProvenanceReadSnapshot.captured(owner, modelPath, source, updates, convention);
    }

    @Override
    public EffectiveProvenanceView getEffectiveProvenance() {
        return EffectiveProvenanceView.captured(new TargetContext(owner, modelPath), source, updates, convention);
    }

    @Override
    public String getConfigurationTrace() {
        return PropertyProvenanceRenderer.configuration(getEffectiveProvenance());
    }

    @Override
    public Class<C> getType() {
        return type;
    }

    @Override
    protected Value<? extends C> calculateOwnPresentValue() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::calculateOwnPresentValue);
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
                return PropertyProvenanceDiagnostics.evaluate(this, supplier::getProducer);
            }
        }

        @Override
        public ExecutionTimeValue<? extends C> calculateExecutionTimeValue() {
            try (EvaluationScopeContext ignored = openScope()) {
                return PropertyProvenanceDiagnostics.evaluate(this, supplier::calculateExecutionTimeValue);
            }
        }

        @Override
        protected Value<? extends C> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext ignored = openScope()) {
                return PropertyProvenanceDiagnostics.evaluate(this, () -> supplier.calculateValue(consumer));
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
                return PropertyProvenanceDiagnostics.evaluate(this, supplier::getProducer);
            }
        }

        @Override
        public ExecutionTimeValue<? extends Map<K, V>> calculateExecutionTimeValue() {
            try (EvaluationScopeContext ignored = openScope()) {
                return PropertyProvenanceDiagnostics.evaluate(this, supplier::calculateExecutionTimeValue);
            }
        }

        @Override
        protected Value<? extends Map<K, V>> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext ignored = openScope()) {
                return PropertyProvenanceDiagnostics.evaluate(this, () -> supplier.calculateValue(consumer));
            }
        }
    }

    @Override
    public <S> ProviderInternal<S> map(Transformer<? extends @Nullable S, ? super C> transformer) {
        return DiagnosticProvider.derived(super.map(transformer), this, ProviderBoundary.MAP);
    }

    @Override
    public ProviderInternal<C> filter(Spec<? super C> spec) {
        return DiagnosticProvider.derived(super.filter(spec), this, ProviderBoundary.FILTER);
    }

    @Override
    public <S> Provider<S> flatMap(Transformer<? extends @Nullable Provider<? extends S>, ? super C> transformer) {
        return DiagnosticProvider.derived(super.flatMap(transformer), this, ProviderBoundary.FLAT_MAP);
    }

    @Override
    public Provider<C> orElse(C value) {
        return DiagnosticProvider.derived(super.orElse(value), this, ProviderBoundary.OR_ELSE);
    }

    @Override
    public Provider<C> orElse(Provider<? extends C> provider) {
        return DiagnosticProvider.derived(super.orElse(provider), this, ProviderBoundary.OR_ELSE);
    }

    @Override
    public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super C, ? super U, ? extends R> combiner) {
        return DiagnosticProvider.derived(super.zip(right, combiner), this, ProviderBoundary.ZIP);
    }

    @Override
    public C get() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::get);
    }

    @Override
    @Nullable
    public C getOrNull() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::getOrNull);
    }

    @Override
    public C getOrElse(C defaultValue) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> super.getOrElse(defaultValue));
    }

}
