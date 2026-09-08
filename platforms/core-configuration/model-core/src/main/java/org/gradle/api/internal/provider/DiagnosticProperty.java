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
import org.gradle.api.internal.provenance.FailedOperation;
import org.gradle.api.internal.provenance.OrdinaryProvenanceState;
import org.gradle.api.internal.provenance.ProvenanceRenderer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.api.specs.Spec;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

/** Opt-in, failure-only reporting adapter. Successful operations perform no diagnostic formatting. */
public final class DiagnosticProperty<T> extends AttributedProperty<T> {
    public DiagnosticProperty(PropertyProvenanceHost host, Class<T> type) {
        super(host, type);
    }

    /** Internal explicit explanation seam; callers choose whether to print the returned report. */
    @Override
    public String getConfigurationTrace() {
        return ProvenanceRenderer.configuration(getEffectiveProvenance());
    }

    @Override
    protected ProvenanceSnapshot<T> newSnapshot(ProviderInternal<? extends T> supplier, OrdinaryProvenanceState state, String modelPath) {
        return new DiagnosticProvenanceSnapshot<>(getType(), supplier, state, modelPath);
    }

    @Override
    protected Value<? extends T> calculateOwnPresentValue() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::calculateOwnPresentValue);
    }

    @Override
    public void set(@Nullable T value) {
        try {
            super.set(value);
        } catch (RuntimeException failure) {
            throw rejected(failure, "set");
        }
    }

    @Override
    public void set(Provider<? extends T> provider) {
        try {
            super.set(provider);
        } catch (RuntimeException failure) {
            throw rejected(failure, "set");
        }
    }

    @Override
    public Property<T> convention(@Nullable T value) {
        try {
            return super.convention(value);
        } catch (RuntimeException failure) {
            throw rejected(failure, "convention");
        }
    }

    @Override
    public Property<T> convention(Provider<? extends T> provider) {
        try {
            return super.convention(provider);
        } catch (RuntimeException failure) {
            throw rejected(failure, "convention");
        }
    }

    @Override
    public Property<T> unset() {
        try {
            return super.unset();
        } catch (RuntimeException failure) {
            throw rejected(failure, "unset");
        }
    }

    @Override
    public Property<T> unsetConvention() {
        try {
            return super.unsetConvention();
        } catch (RuntimeException failure) {
            throw rejected(failure, "unsetConvention");
        }
    }

    @Override
    protected SupportsConvention setToConvention() {
        try {
            return super.setToConvention();
        } catch (RuntimeException failure) {
            throw rejected(failure, "setToConvention");
        }
    }

    @Override
    protected SupportsConvention setToConventionIfUnset() {
        try {
            return super.setToConventionIfUnset();
        } catch (RuntimeException failure) {
            throw rejected(failure, "setToConventionIfUnset");
        }
    }

    @Override
    public void replace(Transformer<? extends @Nullable Provider<? extends T>, ? super Provider<T>> transformation) {
        try {
            super.replace(transformation);
        } catch (RuntimeException failure) {
            throw rejected(failure, "replace");
        }
    }

    private RuntimeException rejected(RuntimeException failure, String operation) {
        // A diagnostic fallback must not mask the rejected operation's original failure.
        try {
            @Nullable Attribution attribution;
            try {
                attribution = failureAttribution();
            } catch (RuntimeException unavailable) {
                attribution = null;
            }
            return PropertyProvenanceDiagnostics.mutation(failure, getEffectiveProvenance(), new FailedOperation(operation, attribution));
        } catch (RuntimeException unavailable) {
            return failure;
        }
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
    public void finalizeValue() {
        PropertyProvenanceDiagnostics.evaluate(this, () -> {
            super.finalizeValue();
            return null;
        });
    }

    @Override
    public void setFromAnyValue(Object value) {
        try {
            super.setFromAnyValue(value);
        } catch (RuntimeException failure) {
            throw rejected(failure, "set");
        }
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
