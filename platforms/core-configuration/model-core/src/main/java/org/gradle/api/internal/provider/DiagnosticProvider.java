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
import org.gradle.api.internal.provenance.Attribution;
import org.gradle.api.internal.provenance.EffectiveProvenanceView.ProviderBoundary;
import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.ProvenanceReadSnapshot;
import org.gradle.api.internal.provenance.ProviderOperation;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

/** Opt-in context for a derived provider; the delegate remains the only evaluator. */
final class DiagnosticProvider<T> extends AbstractMinimalProvider<T> implements ProvenanceAware {
    private final ProviderInternal<T> delegate;
    private final ProvenanceAware source;
    private final ProvenanceAware attributionSource;
    private final ProviderOperation boundary;

    private DiagnosticProvider(ProviderInternal<T> delegate, ProvenanceAware source, ProviderOperation boundary) {
        this.delegate = delegate;
        this.source = source;
        this.attributionSource = originalSource(source);
        this.boundary = boundary;
    }

    static <T> ProviderInternal<T> derived(Provider<T> delegate, ProvenanceAware source, ProviderBoundary boundary) {
        return new DiagnosticProvider<>(Providers.internal(delegate), source, new ProviderOperation(boundary, null));
    }

    DiagnosticProvider<T> declaredBy(Attribution attribution) {
        return new DiagnosticProvider<>(delegate, source, new ProviderOperation(boundary.getKind(), attribution));
    }

    static ProvenanceAware originalSource(ProvenanceAware source) {
        return source instanceof DiagnosticProvider ? ((DiagnosticProvider<?>) source).attributionSource : source;
    }

    @Override
    public ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
        ProviderInternal<T> supplier = super.asSupplier(owner, targetType, sanitizer);
        // Preserve the same declaration/source across the engine's type-sanitizing wrapper.
        return supplier == this ? this : new DiagnosticProvider<>(supplier, source, boundary);
    }

    ProviderInternal<T> getDelegate() {
        return delegate;
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
    public EffectiveProvenanceView getEffectiveProvenance() {
        return getProvenanceReadSnapshot().toView();
    }

    @Override
    public ProvenanceReadSnapshot getProvenanceReadSnapshot() {
        List<ProviderOperation> boundaries = new ArrayList<>();
        boundaries.add(boundary);
        ProvenanceAware input = source;
        while (input instanceof DiagnosticProvider) {
            DiagnosticProvider<?> derived = (DiagnosticProvider<?>) input;
            boundaries.add(derived.boundary);
            input = derived.source;
        }
        Collections.reverse(boundaries);
        return input.getProvenanceReadSnapshot().throughOperations(boundaries);
    }

    @Override
    @Nullable
    public Class<T> getType() {
        return delegate.getType();
    }

    @Override
    public ValueProducer getProducer() {
        return PropertyProvenanceDiagnostics.evaluate(this, delegate::getProducer);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> delegate.calculatePresence(consumer));
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return PropertyProvenanceDiagnostics.evaluate(this, delegate::calculateExecutionTimeValue);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> delegate.calculateValue(consumer));
    }

    @Override
    protected Value<? extends T> calculateOwnPresentValue() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::calculateOwnPresentValue);
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
