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
import org.gradle.api.internal.provider.CollectionPropertyProvenance.Operation;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.api.specs.Spec;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiFunction;

/** Opt-in collection attribution and failure reporting at the existing engine mutation boundaries. */
public final class DiagnosticListProperty<T> extends DefaultListProperty<T> implements CollectionPropertyDiagnostics {
    private final CollectionPropertyProvenance provenance;

    public DiagnosticListProperty(PropertyProvenanceHost host, Class<T> elementType) {
        super(host, elementType);
        provenance = new CollectionPropertyProvenance(host);
    }

    @Override
    public CollectionPropertyProvenance getProvenance() {
        return provenance;
    }

    @Override
    public CollectionProvenanceSnapshot<List<T>> shallowCopy() {
        return provenance.snapshot(getType(), captureSupplier(), getDeclaredDisplayName());
    }

    @Override
    public void set(@Nullable Iterable<? extends T> value) {
        long token = provenance.begin(Operation.SET);
        try {
            super.set(value);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.SET, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    public void set(Provider<? extends Iterable<? extends T>> provider) {
        long token = provenance.begin(Operation.SET);
        try {
            super.set(provider);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.SET, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    public org.gradle.api.provider.ListProperty<T> empty() {
        long token = provenance.begin(Operation.EMPTY);
        try {
            return super.empty();
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.EMPTY, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    public org.gradle.api.provider.ListProperty<T> convention(@Nullable Iterable<? extends T> value) {
        long token = provenance.begin(Operation.CONVENTION);
        try {
            return super.convention(value);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.CONVENTION, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    public org.gradle.api.provider.ListProperty<T> convention(Provider<? extends Iterable<? extends T>> provider) {
        long token = provenance.begin(Operation.CONVENTION);
        try {
            return super.convention(provider);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.CONVENTION, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    protected void setSupplier(CollectionSupplier<T, List<T>> supplier) {
        try {
            super.setSupplier(supplier);
            provenance.bound();
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.SET, getDeclaredDisplayName());
        }
    }

    @Override
    protected void setConvention(CollectionSupplier<T, List<T>> supplier) {
        try {
            super.setConvention(supplier);
            provenance.conventionChanged(isNoValueSupplier(supplier), isExplicit(), isNoValueSupplier(captureSupplier()));
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.CONVENTION, getDeclaredDisplayName());
        }
    }

    @Override
    protected void discardValue() {
        try {
            super.discardValue();
            provenance.cleared(false, isExplicit(), isNoValueSupplier(captureSupplier()));
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.UNSET, getDeclaredDisplayName());
        }
    }

    @Override
    protected void discardConvention() {
        try {
            super.discardConvention();
            provenance.cleared(true, isExplicit(), isNoValueSupplier(captureSupplier()));
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.UNSET_CONVENTION, getDeclaredDisplayName());
        }
    }

    @Override
    protected SupportsConvention setToConventionIfUnset() {
        boolean changed = !isExplicit() && !isDefaultConvention();
        try {
            SupportsConvention result = super.setToConventionIfUnset();
            if (changed) {
                provenance.conventionPromoted(isNoValueSupplier(captureSupplier()));
            }
            return result;
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.SET_TO_CONVENTION_IF_UNSET, getDeclaredDisplayName());
        }
    }

    @Override
    protected SupportsConvention setToConvention() {
        try {
            SupportsConvention result = super.setToConvention();
            provenance.conventionPromoted(isNoValueSupplier(captureSupplier()));
            return result;
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.SET_TO_CONVENTION, getDeclaredDisplayName());
        }
    }

    @Override
    protected void withActualValue(Runnable action, boolean bulk) {
        Operation operation = bulk ? Operation.APPEND_ALL : Operation.APPEND;
        long token = provenance.begin(operation);
        try {
            super.withActualValue(action, bulk);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, operation, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    protected void addExplicitCollector(Collector<T> collector) {
        boolean single = collector instanceof Collectors.SingleElement || collector instanceof Collectors.ElementFromProvider;
        Operation operation = single ? Operation.ADD : Operation.ADD_ALL;
        long token = provenance.begin(operation);
        try {
            assertCanMutate();
            boolean explicit = isExplicit();
            boolean missing = isNoValueSupplier(getExplicitValue(getDefaultValue()));
            super.addExplicitCollector(collector);
            provenance.collectionContribution(single, explicit, !missing);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, operation, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    public void replace(Transformer<? extends @Nullable Provider<? extends Iterable<? extends T>>, ? super Provider<List<T>>> transformation) {
        long token = provenance.begin(Operation.REPLACE);
        try {
            replaceValue(transformation);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.REPLACE, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    private void replaceValue(Transformer<? extends @Nullable Provider<? extends Iterable<? extends T>>, ? super Provider<List<T>>> transformation) {
        CollectionProvenanceSnapshot<List<T>> previous = shallowCopy();
        Provider<? extends Iterable<? extends T>> candidate = transformation.transform(previous);
        if (candidate == null) {
            super.set((Iterable<? extends T>) null);
            return;
        }
        provenance.applyingReplacement();
        super.set(candidate);
        provenance.replaced(candidate, previous);
    }

    @Override
    protected Value<? extends List<T>> calculateOwnPresentValue() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::calculateOwnPresentValue);
    }

    @Override
    protected CollectionSupplier<T, List<T>> finalValue(EvaluationScopeContext context, CollectionSupplier<T, List<T>> supplier, ValueConsumer consumer) {
        EffectiveProvenanceView checkpoint = provenance.beforeFinalization(getDeclaredDisplayName());
        CollectionSupplier<T, List<T>> result = super.finalValue(context, supplier, consumer);
        provenance.finalized(checkpoint);
        return result;
    }

    @Override
    public <S> ProviderInternal<S> map(Transformer<? extends @Nullable S, ? super List<T>> transformer) {
        return DiagnosticProvider.derived(super.map(transformer), this, ProviderBoundary.MAP);
    }

    @Override
    public ProviderInternal<List<T>> filter(Spec<? super List<T>> spec) {
        return DiagnosticProvider.derived(super.filter(spec), this, ProviderBoundary.FILTER);
    }

    @Override
    public <S> Provider<S> flatMap(Transformer<? extends @Nullable Provider<? extends S>, ? super List<T>> transformer) {
        return DiagnosticProvider.derived(super.flatMap(transformer), this, ProviderBoundary.FLAT_MAP);
    }

    @Override
    public Provider<List<T>> orElse(List<T> value) {
        return DiagnosticProvider.derived(super.orElse(value), this, ProviderBoundary.OR_ELSE);
    }

    @Override
    public Provider<List<T>> orElse(Provider<? extends List<T>> provider) {
        return DiagnosticProvider.derived(super.orElse(provider), this, ProviderBoundary.OR_ELSE);
    }

    @Override
    public <U, R> Provider<R> zip(Provider<U> right, BiFunction<? super List<T>, ? super U, ? extends R> combiner) {
        return DiagnosticProvider.derived(super.zip(right, combiner), this, ProviderBoundary.ZIP);
    }

    @Override
    protected Value<? extends List<T>> calculateOwnValue(ValueConsumer consumer) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> super.calculateOwnValue(consumer));
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> super.calculatePresence(consumer));
    }

    @Override
    public ExecutionTimeValue<? extends List<T>> calculateExecutionTimeValue() {
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
            throw provenance.rejected(failure, Operation.SET, getDeclaredDisplayName());
        }
    }

    @Override
    public void add(T value) {
        try {
            super.add(value);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.ADD, getDeclaredDisplayName());
        }
    }

    @Override
    public void add(Provider<? extends T> value) {
        try {
            super.add(value);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.ADD, getDeclaredDisplayName());
        }
    }

    @Override
    public void addAll(Provider<? extends Iterable<? extends T>> values) {
        try {
            super.addAll(values);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.ADD_ALL, getDeclaredDisplayName());
        }
    }

    @Override
    public List<T> get() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::get);
    }

    @Override
    @Nullable
    public List<T> getOrNull() {
        return PropertyProvenanceDiagnostics.evaluate(this, super::getOrNull);
    }

    @Override
    public List<T> getOrElse(List<T> defaultValue) {
        return PropertyProvenanceDiagnostics.evaluate(this, () -> super.getOrElse(defaultValue));
    }

}
