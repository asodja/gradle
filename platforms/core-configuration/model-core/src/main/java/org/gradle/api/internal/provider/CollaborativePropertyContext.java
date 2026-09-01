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
    private static final ThreadLocal<CollaborativePropertyMutation> CURRENT = new ThreadLocal<>();

    private CollaborativePropertyContext() {
    }

    /**
     * Enters the context used for a declarative source binding.
     */
    public static Scope source() {
        return enter(CollaborativePropertyMutation.source(null));
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
        return enter(CollaborativePropertyMutation.contributor(contributor, origin));
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
    static CollaborativePropertyMutation currentMutation() {
        return CURRENT.get();
    }

    private static Scope enter(CollaborativePropertyMutation mutation) {
        CollaborativePropertyMutation previous = CURRENT.get();
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

}
