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
 * Describes the user code responsible for mutating a collaborative property.
 */
public final class CollaborativePropertyMutation {
    private final boolean source;
    @Nullable
    private final String contributor;
    @Nullable
    private final String origin;

    private CollaborativePropertyMutation(boolean source, @Nullable String contributor, @Nullable String origin) {
        this.source = source;
        this.contributor = contributor;
        this.origin = origin;
    }

    public static CollaborativePropertyMutation source(@Nullable String origin) {
        return new CollaborativePropertyMutation(true, null, origin);
    }

    public static CollaborativePropertyMutation contributor(String contributor, @Nullable String origin) {
        if (contributor == null || contributor.isEmpty()) {
            throw new IllegalArgumentException("A collaborative property contributor must have a non-empty name.");
        }
        return new CollaborativePropertyMutation(false, contributor, origin);
    }

    public boolean isSource() {
        return source;
    }

    @Nullable
    public String getContributor() {
        return contributor;
    }

    @Nullable
    public String getOrigin() {
        return origin;
    }
}
