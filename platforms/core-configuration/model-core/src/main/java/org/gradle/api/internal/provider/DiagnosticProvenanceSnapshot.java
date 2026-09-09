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
import org.gradle.api.internal.provenance.OrdinaryProvenanceState;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

/** Missing-value reporting for a captured supplier, without retaining its property or mutable state. */
public final class DiagnosticProvenanceSnapshot<T> extends ProvenanceSnapshot<T> {
    DiagnosticProvenanceSnapshot(Class<T> type, ProviderInternal<? extends T> supplier, OrdinaryProvenanceState state, String modelPath) {
        super(type, supplier, state, modelPath);
    }

    @Override
    public String getConfigurationTrace() {
        return PropertyProvenanceRenderer.configuration(getEffectiveProvenance());
    }

    @Override
    protected Value<? extends T> calculateOwnPresentValue() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::calculateOwnPresentValue);
    }

    @Override
    public <S> ProviderInternal<S> map(Transformer<? extends @Nullable S, ? super T> transformer) {
        return DiagnosticProvider.derived(super.map(transformer), this, ProviderBoundary.MAP);
    }

    @Override
    public ProviderInternal<T> filter(Spec<? super T> spec) {
        return DiagnosticProvider.derived(super.filter(spec), this, ProviderBoundary.FILTER);
    }

    @Override
    public <S> Provider<S> flatMap(Transformer<? extends @Nullable Provider<? extends S>, ? super T> transformer) {
        return DiagnosticProvider.derived(super.flatMap(transformer), this, ProviderBoundary.FLAT_MAP);
    }

    @Override
    public Provider<T> orElse(T value) {
        return DiagnosticProvider.derived(super.orElse(value), this, ProviderBoundary.OR_ELSE);
    }

    @Override
    public Provider<T> orElse(Provider<? extends T> provider) {
        return DiagnosticProvider.derived(super.orElse(provider), this, ProviderBoundary.OR_ELSE);
    }

    @Override
    public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super T, ? super U, ? extends R> combiner) {
        return DiagnosticProvider.derived(super.zip(right, combiner), this, ProviderBoundary.ZIP);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> super.calculateOwnValue(consumer));
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> super.calculatePresence(consumer));
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::calculateExecutionTimeValue);
    }

    @Override
    public ValueProducer getProducer() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::getProducer);
    }

    @Override
    public T get() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::get);
    }

    @Override
    @Nullable
    public T getOrNull() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::getOrNull);
    }

    @Override
    public T getOrElse(T defaultValue) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> super.getOrElse(defaultValue));
    }

}
