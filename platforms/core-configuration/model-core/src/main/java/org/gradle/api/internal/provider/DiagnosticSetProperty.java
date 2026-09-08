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
import org.gradle.api.internal.provider.CollectionPropertyProvenance.Operation;
import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Opt-in collection attribution and failure reporting at the existing engine mutation boundaries. */
public final class DiagnosticSetProperty<T> extends DefaultSetProperty<T> implements CollectionPropertyDiagnostics {
    private final CollectionPropertyProvenance provenance;

    public DiagnosticSetProperty(PropertyProvenanceHost host, Class<T> elementType) {
        super(host, elementType);
        provenance = new CollectionPropertyProvenance(host);
    }

    @Override
    public CollectionPropertyProvenance getProvenance() {
        return provenance;
    }

    @Override
    public CollectionProvenanceSnapshot<Set<T>> shallowCopy() {
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
    public org.gradle.api.provider.SetProperty<T> empty() {
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
    public org.gradle.api.provider.SetProperty<T> convention(@Nullable Iterable<? extends T> value) {
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
    public org.gradle.api.provider.SetProperty<T> convention(Provider<? extends Iterable<? extends T>> provider) {
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
    protected void setSupplier(CollectionSupplier<T, Set<T>> supplier) {
        try {
            super.setSupplier(supplier);
            provenance.bound();
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.SET, getDeclaredDisplayName());
        }
    }

    @Override
    protected void setConvention(CollectionSupplier<T, Set<T>> supplier) {
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
    public void replace(Transformer<? extends @Nullable Provider<? extends Iterable<? extends T>>, ? super Provider<Set<T>>> transformation) {
        long token = provenance.begin(Operation.REPLACE);
        try {
            replaceValue(transformation);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.REPLACE, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    private void replaceValue(Transformer<? extends @Nullable Provider<? extends Iterable<? extends T>>, ? super Provider<Set<T>>> transformation) {
        CollectionProvenanceSnapshot<Set<T>> previous = shallowCopy();
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
    protected Value<? extends Set<T>> calculateOwnPresentValue() {
        try {
            return super.calculateOwnPresentValue();
        } catch (MissingValueException failure) {
            throw provenance.missing(failure, getDeclaredDisplayName());
        }
    }

    @Override
    protected CollectionSupplier<T, Set<T>> finalValue(EvaluationScopeContext context, CollectionSupplier<T, Set<T>> supplier, ValueConsumer consumer) {
        EffectiveProvenanceView checkpoint = provenance.beforeFinalization(getDeclaredDisplayName());
        CollectionSupplier<T, Set<T>> result = super.finalValue(context, supplier, consumer);
        provenance.finalized(checkpoint);
        return result;
    }
}
