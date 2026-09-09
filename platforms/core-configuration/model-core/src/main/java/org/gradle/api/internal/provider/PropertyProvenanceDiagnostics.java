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

import org.gradle.api.internal.provenance.EffectiveProvenanceView;
import org.gradle.api.internal.provenance.ProvenanceReadSnapshot;
import org.gradle.api.internal.provenance.FailedOperation;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Formats only on failure, preserving mutation causes and evaluation exception identities. */
public final class PropertyProvenanceDiagnostics {
    private PropertyProvenanceDiagnostics() {
    }

    @Nullable
    public static ProvenanceReadSnapshot snapshot(@Nullable Object value) {
        try {
            return value instanceof ProvenanceAware ? ((ProvenanceAware) value).getProvenanceReadSnapshot() : null;
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    public static String validationDetails(String details, @Nullable EffectiveProvenanceView checkpoint) {
        if (checkpoint == null) {
            return details;
        }
        try {
            return details + "\n\n" + PropertyProvenanceRenderer.failure(checkpoint, null);
        } catch (RuntimeException unavailable) {
            return details;
        }
    }

    /** Captures descriptor facts before evaluation can mutate the configured source. */
    public static <T extends @Nullable Object> T evaluate(ProvenanceAware source, Supplier<T> action) {
        ProvenanceReadSnapshot snapshot = snapshot(source);
        try {
            return action.get();
        } catch (RuntimeException failure) {
            if (snapshot == null) {
                throw failure;
            }
            RuntimeException reported;
            try {
                EffectiveProvenanceView checkpoint = snapshot.toView();
                reported = failure instanceof MissingValueException
                    ? missing((MissingValueException) failure, checkpoint) : evaluation(failure, checkpoint);
            } catch (RuntimeException unavailable) {
                throw failure;
            }
            throw reported;
        }
    }

    public static RuntimeException evaluation(RuntimeException failure, EffectiveProvenanceView checkpoint) {
        return annotate(failure, checkpoint, null);
    }

    private static RuntimeException annotate(RuntimeException failure, EffectiveProvenanceView checkpoint, @Nullable FailedOperation operation) {
        try {
            if (!reported(failure)) {
                failure.addSuppressed(new EvaluationContext(PropertyProvenanceRenderer.failure(checkpoint, operation)));
            }
        } catch (RuntimeException unavailable) {
            // Diagnostics must never replace the evaluation failure, even if a custom cause accessor fails.
        }
        return failure;
    }

    private static boolean reported(Throwable failure) {
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof ReportedFailure) {
                return true;
            }
            for (Throwable context : current.getSuppressed()) {
                if (context instanceof ReportedFailure) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final class EvaluationContext extends RuntimeException implements ReportedFailure {
        private EvaluationContext(String message) {
            super(message, null, false, false);
        }
    }

    public static MissingValueException missing(MissingValueException failure, EffectiveProvenanceView view) {
        if (failure instanceof ReportedFailure) {
            return failure;
        }
        try {
            return new ReportedMissingValue(failure.getMessage() + "\n\n" + PropertyProvenanceRenderer.failure(view, null), failure);
        } catch (RuntimeException unavailable) {
            return failure;
        }
    }

    public static RuntimeException mutation(RuntimeException failure, EffectiveProvenanceView view, FailedOperation operation) {
        if (failure instanceof ReportedFailure) {
            return failure;
        }
        if (failure instanceof IllegalArgumentException) {
            return new ReportedIllegalArgument(failure.getMessage() + "\n\n" + PropertyProvenanceRenderer.failure(view, operation), failure);
        }
        if (failure instanceof IllegalStateException) {
            return new ReportedIllegalState(failure.getMessage() + "\n\n" + PropertyProvenanceRenderer.failure(view, operation), failure);
        }
        if (failure instanceof NullPointerException) {
            return new ReportedNullPointer(failure.getMessage() + "\n\n" + PropertyProvenanceRenderer.failure(view, operation), failure);
        }
        return annotate(failure, view, operation);
    }

    private interface ReportedFailure {
    }

    private static final class ReportedMissingValue extends MissingValueException implements ReportedFailure {
        private ReportedMissingValue(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ReportedNullPointer extends NullPointerException implements ReportedFailure {
        private ReportedNullPointer(String message, Throwable cause) {
            super(message);
            initCause(cause);
        }
    }

    private static final class ReportedIllegalArgument extends IllegalArgumentException implements ReportedFailure {
        private ReportedIllegalArgument(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class ReportedIllegalState extends IllegalStateException implements ReportedFailure {
        private ReportedIllegalState(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
