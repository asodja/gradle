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

import java.util.List;

/**
 * Internal controls used by Declarative Gradle to opt a property into collaborative mutation.
 */
public interface CollaborativePropertyInternal {
    /**
     * Enables collaborative mode and defines the owning plugin and global contributor order.
     */
    void enableCollaboration(String owner, List<String> contributorOrder);

    /**
     * Adds a hard ordering constraint local to this property.
     */
    void addCollaborationConstraint(String before, String after);

    boolean isCollaborative();
}
