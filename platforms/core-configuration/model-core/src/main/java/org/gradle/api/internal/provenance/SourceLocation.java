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

package org.gradle.api.internal.provenance;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/** Source-file metadata from an instrumented call, independent of runtime classes and configured values. */
public final class SourceLocation {
    private final String fileName;
    private final int line;
    private final @Nullable String path;

    public SourceLocation(String fileName, int line) {
        this(fileName, line, null);
    }

    public SourceLocation(String fileName, int line, @Nullable String path) {
        this.path = path;
        if (fileName.isEmpty() || line <= 0) {
            throw new IllegalArgumentException("A source location needs a file name and a positive line.");
        }
        this.fileName = fileName;
        this.line = line;
    }

    public String getFileName() {
        return fileName;
    }

    public @Nullable String getPath() {
        return path;
    }

    public int getLine() {
        return line;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof SourceLocation && fileName.equals(((SourceLocation) other).fileName) && line == ((SourceLocation) other).line && Objects.equals(path, ((SourceLocation) other).path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, line, path);
    }
}
