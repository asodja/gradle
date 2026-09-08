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
import org.gradle.api.internal.provenance.OrdinaryProvenanceState;
import org.gradle.api.internal.provenance.SemanticOperation;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;
import org.gradle.internal.DisplayName;

import java.util.Collection;
import java.util.Map;

/** Shared collection adapter state; descriptors never retain this runtime attribution service. */
final class CollectionPropertyProvenance {
    enum Operation {
        SET, CONVENTION, EMPTY, REPLACE, UNSET, UNSET_CONVENTION,
        ADD, ADD_ALL, APPEND, APPEND_ALL, PUT, PUT_ALL, INSERT, INSERT_ALL,
        SET_TO_CONVENTION, SET_TO_CONVENTION_IF_UNSET
    }

    private static final SemanticOperation ADD = SemanticOperation.contribution(SemanticOperation.Shape.ADD);
    private static final SemanticOperation ADD_ALL = SemanticOperation.contribution(SemanticOperation.Shape.ADD_ALL);
    private static final SemanticOperation APPEND = SemanticOperation.contribution(SemanticOperation.Shape.APPEND);
    private static final SemanticOperation APPEND_ALL = SemanticOperation.contribution(SemanticOperation.Shape.APPEND_ALL);
    private static final SemanticOperation PUT = SemanticOperation.contribution(SemanticOperation.Shape.PUT);
    private static final SemanticOperation PUT_ALL = SemanticOperation.contribution(SemanticOperation.Shape.PUT_ALL);
    private static final SemanticOperation INSERT = SemanticOperation.contribution(SemanticOperation.Shape.INSERT);
    private static final SemanticOperation INSERT_ALL = SemanticOperation.contribution(SemanticOperation.Shape.INSERT_ALL);

    final OrdinaryProvenanceState state;
    @Nullable
    private PropertyProvenanceHost host;
    @Nullable
    private Operation operation;
    private boolean adding;
    boolean restoring;
    private boolean preservingConvention;

    CollectionPropertyProvenance(PropertyProvenanceHost host) {
        this.host = host;
        state = new OrdinaryProvenanceState(host.getOwnerScope(), host.newOccurrenceScope());
        state.selectCollectionSource(EffectiveProvenanceView.Source.collectionDefault(false));
    }

    public long begin(Operation next) {
        long token = operation == null ? 0 : operation.ordinal() + 1;
        token = (token << 2) | (adding ? 2 : 0) | (preservingConvention ? 1 : 0);
        boolean contribution = next == Operation.ADD || next == Operation.ADD_ALL || next == Operation.PUT || next == Operation.PUT_ALL;
        if (!adding && !(preservingConvention && contribution)) {
            operation = next;
        }
        if (next == Operation.APPEND || next == Operation.APPEND_ALL || next == Operation.INSERT || next == Operation.INSERT_ALL) {
            preservingConvention = true;
        }
        if (contribution) {
            adding = true;
        }
        return token;
    }

    // Cache enum constants once; Operation.values() would clone an array on every successful exit.
    private static final Operation[] OPERATIONS = Operation.values();

    public void end(long token) {
        int ordinal = (int) (token >>> 2);
        operation = ordinal == 0 ? null : OPERATIONS[ordinal - 1];
        adding = (token & 2) != 0;
        preservingConvention = (token & 1) != 0;
    }

    public void applyingReplacement() {
        adding = true;
    }

    public void bound() {
        if (!adding && !restoring) {
            state.acceptedBinding(attribution(), SemanticOperation.EXPLICIT_BINDING);
        }
    }

    public void conventionChanged(boolean cleared, boolean explicit, boolean missing) {
        if (cleared) {
            state.acceptedClearConvention(attribution(), explicit);
        } else {
            state.acceptedConvention(attribution(), explicit);
        }
        selectedDefault(missing);
    }

    public void cleared(boolean convention, boolean explicit, boolean missing) {
        if (restoring) {
            return;
        }
        if (convention) {
            state.acceptedClearConvention(attribution(), explicit);
        } else {
            state.acceptedClearExplicit(attribution());
        }
        selectedDefault(missing);
    }

    public void collectionContribution(boolean single, boolean wasExplicit, boolean retained) {
        contributed(false, single, wasExplicit, retained);
    }

    public void mapContribution(boolean single, boolean wasExplicit, boolean retained) {
        contributed(true, single, wasExplicit, retained);
    }

    private void contributed(boolean map, boolean single, boolean wasExplicit, boolean retained) {
        SemanticOperation operation;
        if (map) {
            operation = preservingConvention ? (single ? INSERT : INSERT_ALL) : (single ? PUT : PUT_ALL);
        } else {
            operation = preservingConvention ? (single ? APPEND : APPEND_ALL) : (single ? ADD : ADD_ALL);
        }
        if (!wasExplicit) {
            state.selectCollectionSource(EffectiveProvenanceView.Source.collectionDefault(!retained));
        }
        state.acceptedContribution(attribution(), operation, retained);
    }

    public void replaced(Provider<?> candidate, Provider<?> previous) {
        SemanticOperation operation = PropertyUpdateClassifier.classifyReplace(candidate, previous);
        if (operation.getKind() == SemanticOperation.Kind.UPDATE) {
            EffectiveProvenanceView captured = ((CollectionProvenanceSnapshot<?>) previous).getEffectiveProvenance();
            state.acceptedUpdate(attribution(), operation, captured.getSource(), captured.getUpdates());
        } else {
            state.acceptedBinding(attribution(), operation);
        }
    }

    Attribution attribution() {
        return java.util.Objects.requireNonNull(host).currentAttribution();
    }

    void selectedDefault(boolean missing) {
        if (state.getSource().getKnowledge() == EffectiveProvenanceView.SourceKnowledge.UNCONFIGURED) {
            state.selectCollectionSource(EffectiveProvenanceView.Source.collectionDefault(missing));
        }
    }

    public void conventionPromoted(boolean missingDefault) {
        if (!preservingConvention) {
            state.acceptedPromotion(attribution());
        } else if (state.getConvention() != null) {
            state.selectCollectionSource(EffectiveProvenanceView.Source.known(EffectiveProvenanceView.SourceSelection.EXPLICIT, state.getConvention()));
        }
        selectedDefault(missingDefault);
    }

    public EffectiveProvenanceView beforeFinalization(@Nullable DisplayName name) {
        return state.getEffectiveProvenance(modelPath(name));
    }

    public void finalized(EffectiveProvenanceView checkpoint) {
        state.freeze(checkpoint);
        host = null;
    }

    static String modelPath(@Nullable DisplayName name) {
        return name == null ? "'unnamed property'" : name.getDisplayName();
    }

    public MissingValueException missing(MissingValueException failure, @Nullable DisplayName name) {
        return PropertyProvenanceDiagnostics.missing(failure, state.getEffectiveProvenance(modelPath(name)));
    }

    public <E, C extends Collection<E>> CollectionProvenanceSnapshot<C> snapshot(Class<C> type, CollectionSupplier<E, C> supplier, @Nullable DisplayName name) {
        return CollectionProvenanceSnapshot.collection(type, supplier, state, modelPath(name));
    }

    public <K, V> CollectionProvenanceSnapshot<Map<K, V>> snapshot(Class<Map<K, V>> type, MapSupplier<K, V> supplier, @Nullable DisplayName name) {
        return CollectionProvenanceSnapshot.map(type, supplier, state, modelPath(name));
    }

    private static String operationName(Operation operation) {
        switch (operation) {
            case UNSET_CONVENTION: return "unsetConvention";
            case SET_TO_CONVENTION: return "setToConvention";
            case SET_TO_CONVENTION_IF_UNSET: return "setToConventionIfUnset";
            case ADD_ALL: return "addAll";
            case APPEND_ALL: return "appendAll";
            case PUT_ALL: return "putAll";
            case INSERT_ALL: return "insertAll";
            default: return operation.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public RuntimeException rejected(RuntimeException failure, Operation operation, @Nullable DisplayName name) {
        try {
            Attribution caller = null;
            try {
                if (host != null) {
                    caller = host.currentAttribution();
                }
            } catch (RuntimeException unavailable) {
                // Preserve the rejection even when its caller cannot be attributed.
            }
            return PropertyProvenanceDiagnostics.mutation(failure, state.getEffectiveProvenance(modelPath(name)), new FailedOperation(operationName(this.operation == null ? operation : this.operation), caller));
        } catch (RuntimeException unavailable) {
            return failure;
        }
    }
}
