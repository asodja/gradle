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

import org.gradle.api.internal.provenance.ProvenanceReadSnapshot;
import org.gradle.api.internal.provenance.EffectiveProvenanceView;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Walks selected scalar binding relationships without querying values, presence, or producers. */
final class PropertyProvenanceTraversal {
    private static final int BINDING_LIMIT = EffectiveProvenanceView.INPUT_DEPTH_LIMIT;
    private static final ThreadLocal<Set<Object>> ACTIVE = new ThreadLocal<>();

    private PropertyProvenanceTraversal() {
    }

    static ProvenanceReadSnapshot withInput(Object owner, ProvenanceReadSnapshot local, Object supplier) {
        if (!(supplier instanceof ProvenanceAware)) {
            return local;
        }
        Set<Object> active = ACTIVE.get();
        boolean outermost = active == null;
        if (outermost) {
            active = Collections.newSetFromMap(new IdentityHashMap<>());
            ACTIVE.set(active);
        }
        if (active.contains(owner)) {
            return local.withPartialReason("Provider binding cycle; further input configuration omitted.");
        }
        if (active.size() == BINDING_LIMIT) {
            return local.withPartialReason("Provider binding depth limit; further input configuration omitted.");
        }
        active.add(owner);
        try {
            return local.withInput(((ProvenanceAware) supplier).getProvenanceReadSnapshot());
        } catch (RuntimeException unavailable) {
            return local.withPartialReason("Bound provider configuration unavailable.");
        } finally {
            active.remove(owner);
            if (outermost) {
                ACTIVE.remove();
            }
        }
    }
}
