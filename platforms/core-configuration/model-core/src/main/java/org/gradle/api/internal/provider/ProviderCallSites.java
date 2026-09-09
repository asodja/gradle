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
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Records declaration sites while forwarding provider construction to the existing implementation. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class ProviderCallSites {
    private static final Attribution UNKNOWN = new Attribution(new ContributorKey("unknown", ContributorKey.Kind.UNKNOWN, ""),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.UNKNOWN, "", "unknown code"), new ScopeIdentity("unknown", "unknown"), null);

    private ProviderCallSites() {
    }

    private static <P extends Provider> P call(Provider source, @Nullable SourceLocation location, Supplier<P> action) {
        if (!(source instanceof ProvenanceAware) || location == null) {
            return action.get();
        }
        Attribution attribution = currentAttribution((ProvenanceAware) source);
        P result = action.get();
        return result instanceof DiagnosticProvider
            ? (P) ((DiagnosticProvider<?>) result).declaredBy(attribution.withLocation(location)) : result;
    }

    private static Attribution currentAttribution(ProvenanceAware source) {
        try {
            ProvenanceAware original = DiagnosticProvider.originalSource(source);
            @Nullable Attribution attribution = null;
            if (original instanceof AttributedProperty) {
                attribution = ((AttributedProperty<?>) original).failureAttribution();
            } else if (original instanceof CollectionPropertyDiagnostics) {
                attribution = ((CollectionPropertyDiagnostics) original).getProvenance().attribution();
            }
            return attribution == null ? UNKNOWN : attribution;
        } catch (RuntimeException unavailable) {
            return UNKNOWN;
        }
    }

    public static Provider map(Provider source, Transformer transformer, @Nullable SourceLocation location) {
        return call(source, location, () -> source.map(transformer));
    }

    public static ProviderInternal map(ProviderInternal source, Transformer transformer, @Nullable SourceLocation location) {
        return call(source, location, () -> source.map(transformer));
    }

    public static Provider filter(Provider source, Spec spec, @Nullable SourceLocation location) {
        return call(source, location, () -> source.filter(spec));
    }

    public static ProviderInternal filter(AbstractMinimalProvider source, Spec spec, @Nullable SourceLocation location) {
        return call(source, location, () -> source.filter(spec));
    }

    public static Provider flatMap(Provider source, Transformer transformer, @Nullable SourceLocation location) {
        return call(source, location, () -> source.flatMap(transformer));
    }

    public static Provider orElse(Provider source, Object value, @Nullable SourceLocation location) {
        return call(source, location, () -> source.orElse(value));
    }

    public static Provider orElse(Provider source, Provider fallback, @Nullable SourceLocation location) {
        return call(source, location, () -> source.orElse(fallback));
    }

    public static Provider zip(Provider source, Provider right, BiFunction combiner, @Nullable SourceLocation location) {
        return call(source, location, () -> source.zip(right, combiner));
    }

    public static Provider getting(MapProperty source, Object key, @Nullable SourceLocation location) {
        return call(source, location, () -> source.getting(key));
    }

    public static Provider keySet(MapProperty source, @Nullable SourceLocation location) {
        return call(source, location, () -> source.keySet());
    }

    public static Provider zip(ProviderFactory factory, Provider left, Provider right, BiFunction combiner, @Nullable SourceLocation location) {
        return call(left, location, () -> factory.zip(left, right, combiner));
    }
}
