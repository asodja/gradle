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

import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.api.internal.provenance.ProvenanceCheckpoint;
import org.gradle.internal.state.ManagedFactory;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/** Keeps provenance transport outside the ordinary managed value factories. */
public final class ProvenanceManagedFactory implements ManagedFactory {
    private final PropertyFactory propertyFactory;
    private final Function<PropertyFactory, ManagedFactory> factory;
    private final ManagedFactory delegate;

    public ProvenanceManagedFactory(PropertyFactory propertyFactory, Function<PropertyFactory, ManagedFactory> factory) {
        this.propertyFactory = propertyFactory;
        this.factory = factory;
        this.delegate = factory.apply(propertyFactory);
    }

    @Override
    public int getId() {
        return delegate.getId();
    }

    @Override
    @Nullable
    public <T> T fromState(Class<T> type, Object state) {
        if (!(state instanceof PropertyProvenanceTransport.ManagedState)) {
            return delegate.fromState(type, state);
        }
        PropertyProvenanceTransport.ManagedState transported = (PropertyProvenanceTransport.ManagedState) state;
        RestoringPropertyFactory restoringFactory = new RestoringPropertyFactory(
            PropertyProvenanceTransport.factory(propertyFactory, transported.getCheckpoint()), ProvenanceCheckpoint.decode(transported.getCheckpoint())
        );
        try {
            T result = factory.apply(restoringFactory).fromState(type, transported.getValue());
            if (result instanceof ProviderInternal && !(result instanceof RestorableProvenance)) {
                return type.cast(PropertyProvenanceTransport.provider((ProviderInternal<?>) result, transported.getCheckpoint()));
            }
            return result;
        } finally {
            restoringFactory.finish();
        }
    }

    private static final class RestoringPropertyFactory implements PropertyFactory {
        private final PropertyFactory delegate;
        private final ProvenanceCheckpoint checkpoint;
        @Nullable
        private RestorableProvenance property;

        private RestoringPropertyFactory(PropertyFactory delegate, ProvenanceCheckpoint checkpoint) {
            this.delegate = delegate;
            this.checkpoint = checkpoint;
        }

        private <T> T begin(T value) {
            property = (RestorableProvenance) value;
            property.beginProvenanceRestore();
            return value;
        }

        private void finish() {
            if (property != null) {
                property.restoreProvenance(checkpoint);
            }
        }

        @Override
        public PropertyFactory withProvenance(ScopeIdentity owner) {
            return new RestoringPropertyFactory(delegate.withProvenance(owner), checkpoint);
        }

        @Override
        @Deprecated
        public DefaultProperty<?> propertyWithNoType() {
            return begin(delegate.propertyWithNoType());
        }

        @Override
        @Deprecated
        public <T> DefaultProperty<T> propertyOfAnyType(Class<T> type) {
            return begin(delegate.propertyOfAnyType(type));
        }

        @Override
        public <T> DefaultProperty<T> property(Class<T> type) {
            return begin(delegate.property(type));
        }

        @Override
        public <T> DefaultListProperty<T> listProperty(Class<T> elementType) {
            return begin(delegate.listProperty(elementType));
        }

        @Override
        public <T> DefaultSetProperty<T> setProperty(Class<T> elementType) {
            return begin(delegate.setProperty(elementType));
        }

        @Override
        public <V, K> DefaultMapProperty<K, V> mapProperty(Class<K> keyType, Class<V> valueType) {
            return begin(delegate.mapProperty(keyType, valueType));
        }
    }
}
