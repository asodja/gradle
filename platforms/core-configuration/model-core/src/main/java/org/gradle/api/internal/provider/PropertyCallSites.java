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
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.api.internal.provenance.SourceLocation;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.provider.SupportsConvention;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

/** Typed targets for instrumented calls. Each call scopes its location to the receiving diagnostic adapter. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class PropertyCallSites {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();
    private static final Attribution UNKNOWN = new Attribution(new ContributorKey("unknown", ContributorKey.Kind.UNKNOWN, ""),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, "", "unknown code"), new ScopeIdentity("unknown", "unknown"), null);

    private PropertyCallSites() {
    }

    /** Scopes source metadata while a language-specific adapter invokes the original operation. */
    public static <T extends @Nullable Object> T withLocation(Object property, @Nullable SourceLocation location, Supplier<T> action) {
        if (!(property instanceof ProvenanceAware) || location == null) {
            return action.get();
        }
        Object receiver = property instanceof CollectionPropertyDiagnostics ? ((CollectionPropertyDiagnostics) property).getProvenance() : property;
        Frame previous = CURRENT.get();
        CURRENT.set(new Frame(receiver, location));
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /** User callbacks must not inherit a caller's location for their own uninstrumented mutations. */
    static <T extends @Nullable Object> T withoutLocation(Supplier<T> action) {
        Frame previous = CURRENT.get();
        CURRENT.remove();
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    static @Nullable Attribution attribution(@Nullable Attribution attribution, Object receiver) {
        Frame frame = CURRENT.get();
        if (frame == null || frame.receiver != receiver) {
            return attribution;
        }
        return (attribution == null ? UNKNOWN : attribution).withLocation(frame.location);
    }

    private static void restore(@Nullable Frame frame) {
        if (frame == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(frame);
        }
    }

    private static final class Frame {
        private final Object receiver;
        private final SourceLocation location;

        private Frame(Object receiver, SourceLocation location) {
            this.receiver = receiver;
            this.location = location;
        }
    }

    public static void set(Property property, @Nullable Object value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static void set(Property property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static Property value(Property property, @Nullable Object value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static Property value(Property property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static Property convention(Property property, @Nullable Object value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static Property convention(Property property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static Property unset(Property property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unset());
    }

    public static Property unsetConvention(Property property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unsetConvention());
    }

    public static void replace(DefaultProperty property, Transformer transformation, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.replace(transformation);
            return null;
        });
    }

    public static void set(ListProperty property, @Nullable Iterable value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static void set(ListProperty property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static ListProperty value(ListProperty property, @Nullable Iterable value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static ListProperty value(ListProperty property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static ListProperty convention(ListProperty property, @Nullable Iterable value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static ListProperty convention(ListProperty property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static ListProperty unset(ListProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unset());
    }

    public static ListProperty unsetConvention(ListProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unsetConvention());
    }

    public static void replace(DefaultListProperty property, Transformer transformation, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.replace(transformation);
            return null;
        });
    }

    public static ListProperty empty(ListProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.empty());
    }

    public static void add(ListProperty property, Object value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.add(value);
            return null;
        });
    }

    public static void add(ListProperty property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.add(value);
            return null;
        });
    }

    public static void append(CollectionPropertyInternal property, Object value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.append(value);
            return null;
        });
    }

    public static void append(CollectionPropertyInternal property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.append(value);
            return null;
        });
    }

    public static void addAll(ListProperty property, Object[] values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void addAll(ListProperty property, Iterable values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void addAll(ListProperty property, Provider values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void appendAll(CollectionPropertyInternal property, Object[] values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.appendAll(values);
            return null;
        });
    }

    public static void appendAll(CollectionPropertyInternal property, Iterable values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.appendAll(values);
            return null;
        });
    }

    public static void appendAll(CollectionPropertyInternal property, Provider values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.appendAll(values);
            return null;
        });
    }

    public static void set(SetProperty property, @Nullable Iterable value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static void set(SetProperty property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static SetProperty value(SetProperty property, @Nullable Iterable value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static SetProperty value(SetProperty property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static SetProperty convention(SetProperty property, @Nullable Iterable value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static SetProperty convention(SetProperty property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static SetProperty unset(SetProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unset());
    }

    public static SetProperty unsetConvention(SetProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unsetConvention());
    }

    public static void replace(DefaultSetProperty property, Transformer transformation, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.replace(transformation);
            return null;
        });
    }

    public static SetProperty empty(SetProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.empty());
    }

    public static void add(SetProperty property, Object value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.add(value);
            return null;
        });
    }

    public static void add(SetProperty property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.add(value);
            return null;
        });
    }

    public static void addAll(SetProperty property, Object[] values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void addAll(SetProperty property, Iterable values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void addAll(SetProperty property, Provider values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void set(MapProperty property, @Nullable Map value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static void set(MapProperty property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static MapProperty value(MapProperty property, @Nullable Map value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static MapProperty value(MapProperty property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static MapProperty convention(MapProperty property, @Nullable Map value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static MapProperty convention(MapProperty property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static MapProperty unset(MapProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unset());
    }

    public static MapProperty unsetConvention(MapProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.unsetConvention());
    }

    public static void replace(DefaultMapProperty property, Transformer transformation, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.replace(transformation);
            return null;
        });
    }

    public static MapProperty empty(MapProperty property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.empty());
    }

    public static void put(MapProperty property, Object key, Object value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.put(key, value);
            return null;
        });
    }

    public static void put(MapProperty property, Object key, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.put(key, value);
            return null;
        });
    }

    public static void insert(MapPropertyInternal property, Object key, Object value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.insert(key, value);
            return null;
        });
    }

    public static void insert(MapPropertyInternal property, Object key, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.insert(key, value);
            return null;
        });
    }

    public static void putAll(MapProperty property, Map values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.putAll(values);
            return null;
        });
    }

    public static void putAll(MapProperty property, Provider values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.putAll(values);
            return null;
        });
    }

    public static void insertAll(MapPropertyInternal property, Map values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.insertAll(values);
            return null;
        });
    }

    public static void insertAll(MapPropertyInternal property, Provider values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.insertAll(values);
            return null;
        });
    }

    public static void set(HasMultipleValues property, @Nullable Iterable value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static void set(HasMultipleValues property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.set(value);
            return null;
        });
    }

    public static HasMultipleValues value(HasMultipleValues property, @Nullable Iterable value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static HasMultipleValues value(HasMultipleValues property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.value(value));
    }

    public static HasMultipleValues convention(HasMultipleValues property, @Nullable Iterable value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static HasMultipleValues convention(HasMultipleValues property, Provider value, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.convention(value));
    }

    public static HasMultipleValues empty(HasMultipleValues property, @Nullable SourceLocation location) {
        return withLocation(property, location, () -> property.empty());
    }

    public static void add(HasMultipleValues property, Object value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.add(value);
            return null;
        });
    }

    public static void add(HasMultipleValues property, Provider value, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.add(value);
            return null;
        });
    }

    public static void addAll(HasMultipleValues property, Object[] values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void addAll(HasMultipleValues property, Iterable values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }

    public static void addAll(HasMultipleValues property, Provider values, @Nullable SourceLocation location) {
        withLocation(property, location, () -> {
            property.addAll(values);
            return null;
        });
    }
    public static SupportsConvention unset(SupportsConvention property, @Nullable SourceLocation location) {
        return withLocation(property, location, property::unset);
    }

    public static SupportsConvention unsetConvention(SupportsConvention property, @Nullable SourceLocation location) {
        return withLocation(property, location, property::unsetConvention);
    }
}
