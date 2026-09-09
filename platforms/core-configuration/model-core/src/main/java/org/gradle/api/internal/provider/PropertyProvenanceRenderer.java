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

import org.gradle.api.internal.provenance.Attribution;
import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.FailedOperation;
import org.gradle.api.internal.provenance.ProvenanceRenderer;
import org.gradle.api.internal.provenance.SourceLocation;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/** Resolves known script locations for the console's native path:line recognition. */
final class PropertyProvenanceRenderer {
    private PropertyProvenanceRenderer() {
    }

    static String configuration(EffectiveProvenanceView view) {
        return ProvenanceRenderer.configuration(view, PropertyProvenanceRenderer::sourcePath);
    }

    static String failure(EffectiveProvenanceView view, @Nullable FailedOperation operation) {
        return ProvenanceRenderer.failure(view, operation, PropertyProvenanceRenderer::sourcePath);
    }

    private static String sourcePath(Attribution attribution) {
        SourceLocation location = Objects.requireNonNull(attribution.getLocation());
        if (location.getPath() != null) {
            try {
                Path path = Paths.get(location.getPath());
                if (validPath(path, location)) {
                    return path.toString();
                }
            } catch (RuntimeException unavailable) {
                // A historical source path can be unavailable on a later run.
            }
        }
        switch (attribution.getOrigin().getKind()) {
            case PROJECT_SCRIPT:
            case APPLIED_SCRIPT:
            case SETTINGS_SCRIPT:
            case INIT_SCRIPT:
                try {
                    URI uri = URI.create(attribution.getOrigin().getIdentifier());
                    if ("file".equalsIgnoreCase(uri.getScheme())) {
                        Path path = Paths.get(uri);

                        // A script's attribution can also be active while a helper from another file runs.
                        // Do not turn that helper's line into a link to the script.
                        if (validPath(path, location)) {
                            return path.toString();
                        }
                    }
                } catch (RuntimeException unavailable) {
                    // Unresolvable locations must not interfere with reporting the original failure.
                }
                break;
            default:
                break;
        }
        return location.getFileName();
    }

    private static boolean validPath(Path path, SourceLocation location) {
        String text = path.toString();
        return path.isAbsolute() && path.getFileName() != null && path.getFileName().toString().equals(location.getFileName())
            && text.length() <= 512 && text.chars().noneMatch(Character::isISOControl) && Files.isRegularFile(path);
    }
}
