/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.initialization.transform;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/** Keeps local plugin source paths tied to the original artifact, before it is copied into the cache. */
@DisableCachingByDefault(because = "Instrumented jars contain local diagnostic source paths.")
public abstract class SourceAwareProjectDependencyInstrumentingArtifactTransform extends ProjectDependencyInstrumentingArtifactTransform {
    @Override
    @InputArtifact
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract Provider<FileSystemLocation> getInput();

    @Override
    protected @Nullable String sourceRoot() {
        Path artifact = getInput().get().getAsFile().toPath().toAbsolutePath().normalize();
        Path parent = artifact.getParent();
        if (parent != null) {
            if (artifact.toString().endsWith(".jar") && parent.endsWith("build/libs")) {
                return parent.getParent().getParent().toString();
            }
            if (artifact.endsWith("build/classes/java/main") || artifact.endsWith("build/classes/kotlin/main")) {
                return parent.getParent().getParent().getParent().toString();
            }
        }
        return null;
    }
}
