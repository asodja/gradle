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
import org.gradle.api.internal.provenance.ContributorKey;
import org.gradle.api.internal.provenance.DiagnosticOrigin;
import org.gradle.api.internal.provenance.EffectiveProvenanceView.ProviderBoundary;
import org.gradle.api.internal.provenance.ProvenanceCheckpoint;
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.BiFunction;

/** Bridges descriptor transport to ordinary property recreation and captured provider evaluation. */
public final class PropertyProvenanceTransport {
    private PropertyProvenanceTransport() {
    }

    public static byte @Nullable [] encodeCheckpoint(Object value) {
        return value instanceof ProvenanceAware ? ((ProvenanceAware) value).getProvenanceCheckpoint().encode() : null;
    }

    public static PropertyFactory factory(PropertyFactory factory, byte @Nullable [] checkpoint) {
        return checkpoint == null ? factory : factory.withProvenance(ProvenanceCheckpoint.decode(checkpoint).getView().getTarget().getOwner());
    }

    /** Restores the property value without recording hydration as a provenance mutation. */
    public static void restore(Object property, byte @Nullable [] checkpoint, Runnable restoreValue) {
        if (checkpoint == null) {
            restoreValue.run();
            return;
        }
        ProvenanceCheckpoint decoded = ProvenanceCheckpoint.decode(checkpoint);
        RestorableProvenance provenance = (RestorableProvenance) property;
        provenance.beginProvenanceRestore();
        try {
            restoreValue.run();
        } finally {
            provenance.restoreProvenance(decoded);
        }
    }

    public static <T> ProviderInternal<T> provider(ProviderInternal<T> provider, byte @Nullable [] checkpoint) {
        return checkpoint == null ? provider : new TransportedProvider<>(provider, ProvenanceCheckpoint.decode(checkpoint));
    }

    /** Used only while recreating an isolated managed value; never part of its value fingerprint. */
    public static final class ManagedState {
        @Nullable
        private final Object value;
        private final byte[] checkpoint;

        public ManagedState(@Nullable Object value, byte[] checkpoint) {
            this.value = value;
            this.checkpoint = checkpoint;
        }

        @Nullable
        public Object getValue() {
            return value;
        }

        public byte[] getCheckpoint() {
            return checkpoint;
        }
    }

    static PropertyProvenanceHost host(PropertyHost delegate, ScopeIdentity owner) {
        return new PropertyProvenanceHost() {
            @Override
            public ScopeIdentity getOwnerScope() {
                return owner;
            }

            @Override
            public String newOccurrenceScope() {
                // New writes to independently recreated branches must not collide with persisted IDs.
                return UUID.randomUUID().toString();
            }

            @Override
            public Attribution currentAttribution() {
                return delegate instanceof PropertyProvenanceHost ? ((PropertyProvenanceHost) delegate).currentAttribution() : UNKNOWN;
            }

            @Override
            @Nullable
            public String beforeRead(@Nullable ModelObject producer) {
                return delegate.beforeRead(producer);
            }
        };
    }

    private static final Attribution UNKNOWN = new Attribution(
        new ContributorKey("transport", ContributorKey.Kind.UNKNOWN, ""),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, "", "unknown code"),
        new ScopeIdentity("unknown", "unknown"), null
    );

    private static final class TransportedProvider<T> extends AbstractMinimalProvider<T> implements ProvenanceAware {
        private final ProviderInternal<T> supplier;
        private final ProvenanceCheckpoint checkpoint;

        private TransportedProvider(ProviderInternal<T> supplier, ProvenanceCheckpoint checkpoint) {
            this.supplier = supplier;
            this.checkpoint = checkpoint;
        }

        @Override
        public ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
            ProviderInternal<T> result = super.asSupplier(owner, targetType, sanitizer);
            return result == this ? this : new TransportedProvider<>(result, checkpoint);
        }

        @Override
        public org.gradle.api.internal.provenance.EffectiveProvenanceView getEffectiveProvenance() {
            return checkpoint.getView();
        }

        @Override
        public ProvenanceCheckpoint getProvenanceCheckpoint() {
            return checkpoint;
        }

        @Override
        public String getConfigurationTrace() {
            return PropertyProvenanceRenderer.configuration(getEffectiveProvenance());
        }

        @Override
        @Nullable
        public Class<T> getType() {
            return supplier.getType();
        }

        @Override
        public ValueProducer getProducer() {
            return PropertyProvenanceDiagnostics.evaluate(this, supplier::getProducer);
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            return PropertyProvenanceDiagnostics.evaluate(this, supplier::calculateExecutionTimeValue);
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return PropertyProvenanceDiagnostics.evaluate(this, () -> supplier.calculateValue(consumer));
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
}
