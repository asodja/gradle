/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import org.gradle.api.internal.provider.PropertyProvenanceTransport;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.state.ManagedFactory;
import org.jspecify.annotations.Nullable;

public class IsolatedManagedValue extends AbstractManagedValueSnapshot<Isolatable<?>> implements Isolatable<Object> {
    private final ManagedFactory factory;
    private final Class<?> targetType;
    private final byte @Nullable [] provenance;

    public IsolatedManagedValue(Class<?> targetType, ManagedFactory factory, Isolatable<?> state) {
        this(targetType, factory, state, null);
    }

    public IsolatedManagedValue(Class<?> targetType, ManagedFactory factory, Isolatable<?> state, byte @Nullable [] provenance) {
        super(state);
        this.targetType = targetType;
        this.factory = factory;
        this.provenance = provenance == null ? null : provenance.clone();
    }

    /** Attaches the checkpoint captured before isolating the value state. */
    IsolatedManagedValue withProvenance(byte[] checkpoint) {
        return new IsolatedManagedValue(targetType, factory, state, checkpoint);
    }

    public byte @Nullable [] getProvenance() {
        return provenance == null ? null : provenance.clone();
    }

    private Object recreatedState() {
        Object value = state.isolate();
        return provenance == null ? value : new PropertyProvenanceTransport.ManagedState(value, provenance);
    }

    @Override
    public ValueSnapshot asSnapshot() {
        return new ManagedValueSnapshot(targetType.getName(), state.asSnapshot());
    }

    @Override
    public Object isolate() {
        return factory.fromState(targetType, recreatedState());
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        if (type.isAssignableFrom(targetType)) {
            return type.cast(isolate());
        }
        return type.cast(factory.fromState(type, recreatedState()));
    }

    public int getFactoryId() {
        return factory.getId();
    }

    public Class<?> getTargetType() {
        return targetType;
    }
}
