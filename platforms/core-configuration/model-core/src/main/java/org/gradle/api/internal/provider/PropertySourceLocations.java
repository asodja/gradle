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

import org.gradle.api.internal.provenance.SourceLocation;
import org.jspecify.annotations.Nullable;

/** Creates descriptor-only call sites for diagnostic receivers, without class loading or source I/O. */
public final class PropertySourceLocations {
    private PropertySourceLocations() {
    }

    public static @Nullable SourceLocation capture(Object receiver, String file, int line) {
        return capture(receiver, file, line, null);
    }

    /** The optional path comes from path-sensitive instrumentation of the original project artifact. */
    public static @Nullable SourceLocation capture(Object receiver, String file, int line, @Nullable String path) {
        return receiver instanceof ProvenanceAware ? new SourceLocation(file, line, path) : null;
    }
}
