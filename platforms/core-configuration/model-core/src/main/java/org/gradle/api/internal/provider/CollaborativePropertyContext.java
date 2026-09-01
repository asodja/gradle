/*
 * Copyright 2026 the original author or authors.
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

import org.jspecify.annotations.Nullable;

/**
 * Attributes mutations made to collaborative properties.
 *
 * <p>Declarative Gradle is expected to enter one of these scopes while invoking user source
 * bindings or reactive plugin actions. The scope is deliberately thread-local: collaborative
 * properties do not make any concurrency guarantees.</p>
 */
public final class CollaborativePropertyContext {
    private static final ThreadLocal<Mutation> CURRENT = new ThreadLocal<>();

    private CollaborativePropertyContext() {
    }

    /**
     * Enters the context used for a declarative source binding.
     */
    public static Scope source() {
        return enter(Mutation.source());
    }

    /**
     * Enters the context used for an attributed contributor action.
     */
    public static Scope contributor(String contributor) {
        return contributor(contributor, null);
    }

    /**
     * Enters the context used for an attributed contributor action with a diagnostic origin.
     */
    public static Scope contributor(String contributor, @Nullable String origin) {
        if (contributor == null || contributor.isEmpty()) {
            throw new IllegalArgumentException("A collaborative property contributor must have a non-empty name.");
        }
        return enter(Mutation.contributor(contributor, origin));
    }

    public static void withSource(Runnable action) {
        try (Scope ignored = source()) {
            action.run();
        }
    }

    public static void withContributor(String contributor, Runnable action) {
        try (Scope ignored = contributor(contributor)) {
            action.run();
        }
    }

    public static void withContributor(String contributor, @Nullable String origin, Runnable action) {
        try (Scope ignored = contributor(contributor, origin)) {
            action.run();
        }
    }

    @Nullable
    static Mutation currentMutation() {
        return CURRENT.get();
    }

    private static Scope enter(Mutation mutation) {
        Mutation previous = CURRENT.get();
        CURRENT.set(mutation);
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    static final class Mutation {
        private final boolean source;
        @Nullable
        private final String contributor;
        @Nullable
        private final String origin;

        private Mutation(boolean source, @Nullable String contributor, @Nullable String origin) {
            this.source = source;
            this.contributor = contributor;
            this.origin = origin;
        }

        static Mutation source() {
            return new Mutation(true, null, null);
        }

        static Mutation contributor(String contributor, @Nullable String origin) {
            return new Mutation(false, contributor, origin);
        }

        boolean isSource() {
            return source;
        }

        @Nullable
        String getContributor() {
            return contributor;
        }

        @Nullable
        String getOrigin() {
            return origin;
        }
    }
}
