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

import java.util.Map;

/** Opt-in collection attribution and failure reporting at the existing engine mutation boundaries. */
public final class DiagnosticMapProperty<K, V> extends DefaultMapProperty<K, V> implements CollectionPropertyDiagnostics {
    private final CollectionPropertyProvenance provenance;

    public DiagnosticMapProperty(PropertyProvenanceHost host, Class<K> keyType, Class<V> valueType) {
        super(host, keyType, valueType);
        provenance = new CollectionPropertyProvenance(host);
    }

    @Override
    public CollectionPropertyProvenance getProvenance() {
        return provenance;
    }

    @Override
    public CollectionProvenanceSnapshot<Map<K, V>> shallowCopy() {
        return provenance.snapshot(getType(), captureSupplier(), getDeclaredDisplayName());
    }

    @Override
    public void set(@Nullable Map<? extends K, ? extends V> value) {
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
    public void set(Provider<? extends Map<? extends K, ? extends V>> provider) {
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
    public org.gradle.api.provider.MapProperty<K, V> empty() {
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
    public org.gradle.api.provider.MapProperty<K, V> convention(@Nullable Map<? extends K, ? extends V> value) {
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
    public org.gradle.api.provider.MapProperty<K, V> convention(Provider<? extends Map<? extends K, ? extends V>> provider) {
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
    protected void setSupplier(MapSupplier<K, V> supplier) {
        try {
            super.setSupplier(supplier);
            provenance.bound();
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.SET, getDeclaredDisplayName());
        }
    }

    @Override
    protected void setConvention(MapSupplier<K, V> supplier) {
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
        Operation operation = bulk ? Operation.INSERT_ALL : Operation.INSERT;
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
    protected void addExplicitCollector(MapCollector<K, V> collector) {
        boolean single = collector instanceof MapCollectors.SingleEntry || collector instanceof MapCollectors.EntryWithValueFromProvider;
        Operation operation = single ? Operation.PUT : Operation.PUT_ALL;
        long token = provenance.begin(operation);
        try {
            assertCanMutate();
            boolean explicit = isExplicit();
            boolean missing = isNoValueSupplier(getExplicitValue(getDefaultValue()));
            super.addExplicitCollector(collector);
            provenance.mapContribution(single, explicit, !missing);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, operation, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    @Override
    public void replace(Transformer<? extends @Nullable Provider<? extends Map<? extends K, ? extends V>>, ? super Provider<Map<K, V>>> transformation) {
        long token = provenance.begin(Operation.REPLACE);
        try {
            replaceValue(transformation);
        } catch (RuntimeException failure) {
            throw provenance.rejected(failure, Operation.REPLACE, getDeclaredDisplayName());
        } finally {
            provenance.end(token);
        }
    }

    private void replaceValue(Transformer<? extends @Nullable Provider<? extends Map<? extends K, ? extends V>>, ? super Provider<Map<K, V>>> transformation) {
        CollectionProvenanceSnapshot<Map<K, V>> previous = shallowCopy();
        Provider<? extends Map<? extends K, ? extends V>> candidate = transformation.transform(previous);
        if (candidate == null) {
            super.set((Map<? extends K, ? extends V>) null);
            return;
        }
        provenance.applyingReplacement();
        super.set(candidate);
        provenance.replaced(candidate, previous);
    }

    @Override
    protected Value<? extends Map<K, V>> calculateOwnPresentValue() {
        try {
            return super.calculateOwnPresentValue();
        } catch (MissingValueException failure) {
            throw provenance.missing(failure, getDeclaredDisplayName());
        }
    }

    @Override
    protected MapSupplier<K, V> finalValue(EvaluationScopeContext context, MapSupplier<K, V> supplier, ValueConsumer consumer) {
        EffectiveProvenanceView checkpoint = provenance.beforeFinalization(getDeclaredDisplayName());
        MapSupplier<K, V> result = super.finalValue(context, supplier, consumer);
        provenance.finalized(checkpoint);
        return result;
    }
}
