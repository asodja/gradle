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

import java.util.Iterator;
import java.util.function.Function;

/** Bounded, stacktrace-like projection of descriptor facts. Never inspects values or provider graphs. */
public final class ProvenanceRenderer {
    private static final int UPDATE_LIMIT = 64;
    private static final int DETAIL_LIMIT = 16;
    private static final int LABEL_LIMIT = 512;

    private final ScopeIdentity owner;
    private final Function<Attribution, String> sourcePath;

    private ProvenanceRenderer(ScopeIdentity owner, Function<Attribution, String> sourcePath) {
        this.owner = owner;
        this.sourcePath = sourcePath;
    }

    public static String configuration(EffectiveProvenanceView view) {
        return configuration(view, attribution -> java.util.Objects.requireNonNull(attribution.getLocation()).getFileName());
    }

    /** The caller may supply resolved source paths; no runtime services are retained by descriptors. */
    public static String configuration(EffectiveProvenanceView view, Function<Attribution, String> sourcePath) {
        return new ProvenanceRenderer(view.getTarget().getOwner(), sourcePath).render(view, null);
    }

    public static String failure(EffectiveProvenanceView view, @Nullable FailedOperation failedOperation) {
        return failure(view, failedOperation, attribution -> java.util.Objects.requireNonNull(attribution.getLocation()).getFileName());
    }

    public static String failure(EffectiveProvenanceView view, @Nullable FailedOperation failedOperation, Function<Attribution, String> sourcePath) {
        return new ProvenanceRenderer(view.getTarget().getOwner(), sourcePath).render(view, failedOperation);
    }

    private String render(EffectiveProvenanceView view, @Nullable FailedOperation failedOperation) {
        StringBuilder result = new StringBuilder(512);
        String modelPath = view.getTarget().getModelPath();
        result.append("Configuration of ").append(label(modelPath));
        boolean showBuild = !owner.getBuildIdentity().equals(":");
        // Task display names contain their project path; extension and unnamed-property labels do not.
        boolean showProject = !owner.getScopePath().equals(":") && !modelPath.startsWith("task '");
        if (showBuild || showProject) {
            result.append(" (");
            if (showBuild) {
                result.append("build '").append(label(owner.getBuildIdentity())).append("'");
                if (showProject) {
                    result.append(", ");
                }
            }
            if (showProject) {
                result.append("project '").append(label(owner.getScopePath())).append("'");
            }
            result.append(')');
        }
        result.append(":\n");
        if (failedOperation != null) {
            frame(result, "failed " + label(failedOperation.getOperation()), failedOperation.getAttribution());
        }
        StringBuilder shadowed = new StringBuilder();
        StringBuilder reasons = new StringBuilder();
        renderBody(result, view, 0, shadowed, reasons);
        return result.append(shadowed).append(reasons).toString().stripTrailing();
    }

    private void renderBody(StringBuilder result, EffectiveProvenanceView view, int depth, StringBuilder shadowed, StringBuilder reasons) {
        if (!view.getProviderOperations().isEmpty()) {
            java.util.List<ProviderOperation> operations = view.getProviderOperations();
            int count = 0;
            for (int i = operations.size() - 1; i >= 0; i--) {
                if (count++ == DETAIL_LIMIT) {
                    result.append("    ... additional provider transformations omitted\n");
                    break;
                }
                ProviderOperation operation = operations.get(i);
                frame(result, operationName(operation.getKind().name()), operation.getAttribution());
                switch (operation.getKind()) {
                    case OR_ELSE:
                    case FLAT_MAP:
                        result.append("        selection unknown\n");
                        break;
                    case ZIP:
                        result.append("        left input shown; other input not traced\n");
                        break;
                    case MAP_ENTRY:
                        result.append("        A missing entry can mean an absent key or an absent map; key ownership is not inferred.\n");
                        break;
                    default:
                        break;
                }
            }
        }
        Iterator<MutationOccurrence> updates = view.getUpdates().reverseIterator();
        int shown = 0;
        while (shown < UPDATE_LIMIT && updates.hasNext()) {
            MutationOccurrence occurrence = updates.next();
            StringBuilder operation = new StringBuilder(occurrence.getOperation().getKind() == SemanticOperation.Kind.CONTRIBUTION ? "" : "update ");
            int shapes = 0;
            for (SemanticOperation.Shape shape : occurrence.getOperation().getShapes()) {
                if (shapes == DETAIL_LIMIT) {
                    operation.append(" -> ...");
                    break;
                }
                if (shapes++ != 0) {
                    operation.append(" -> ");
                }
                operation.append(operationName(shape.name()));
            }
            frame(result, operation.toString(), occurrence.getAttribution());
            shown++;
        }
        if (shown < view.getUpdates().size()) {
            result.append("    ... ").append(view.getUpdates().size() - shown).append(" earlier updates omitted\n");
        }
        source(result, view.getSource(), view.getRootKind());
        if (view.getInput() != null) {
            if (depth == EffectiveProvenanceView.INPUT_DEPTH_LIMIT) {
                result.append("    ... further bound provider configuration omitted\n");
            } else {
                EffectiveProvenanceView input = view.getInput();
                result.append("    → ");
                if (!input.getProviderOperations().isEmpty()) {
                    result.append("provider derived from ");
                }
                result.append(inputLabel(view.getTarget(), input.getTarget())).append('\n');
                StringBuilder nested = new StringBuilder();
                StringBuilder nestedShadowed = new StringBuilder();
                StringBuilder nestedReasons = new StringBuilder();
                renderBody(nested, input, depth + 1, nestedShadowed, nestedReasons);
                nested.append(nestedShadowed).append(nestedReasons).toString().lines().forEach(line -> {
                    if (!line.isEmpty()) {
                        result.append("    ").append(line);
                    }
                    result.append('\n');
                });
            }
        }
        if (!view.getShadowedConfiguration().isEmpty()) {
            if (shadowed.length() == 0) {
                shadowed.append("\n    Overridden:\n");
            }
            int count = 0;
            for (MutationOccurrence binding : view.getShadowedConfiguration()) {
                if (count++ == DETAIL_LIMIT) {
                    shadowed.append("    ... additional shadowed bindings omitted\n");
                    break;
                }
                frame(shadowed, binding.getOperation().getKind() == SemanticOperation.Kind.CONVENTION_BINDING ? "convention" : "set", binding.getAttribution());
            }
        }
        if (!view.getPartialReasons().isEmpty()) {
            if (reasons.length() == 0) {
                reasons.append("Coverage notes:\n");
            }
            int count = 0;
            for (String reason : view.getPartialReasons()) {
                if (count++ == DETAIL_LIMIT) {
                    reasons.append("    ... additional coverage notes omitted\n");
                    break;
                }
                reasons.append("    ").append(label(reason)).append('\n');
            }
        }
    }

    private static String inputLabel(TargetContext target, TargetContext input) {
        String path = input.getModelPath();
        String targetPath = target.getModelPath();
        int property = path.lastIndexOf(" property '");
        int targetProperty = targetPath.lastIndexOf(" property '");
        if (input.getOwner().equals(target.getOwner()) && property >= 0 && targetProperty >= 0
            && path.substring(0, property).equals(targetPath.substring(0, targetProperty))) {
            path = path.substring(property + 1);
        } else if (path.equals("'unnamed property'")) {
            path = "an unnamed property";
        }
        StringBuilder result = new StringBuilder(label(path));
        if (!input.getOwner().equals(target.getOwner())) {
            result.append(" (build '").append(label(input.getOwner().getBuildIdentity()))
                .append("', project '").append(label(input.getOwner().getScopePath())).append("')");
        }
        return result.toString();
    }

    private void source(StringBuilder result, EffectiveProvenanceView.Source source, EffectiveProvenanceView.RootKind rootKind) {
        String selection = source.getSelection() == EffectiveProvenanceView.SourceSelection.CONVENTION ? "convention" : "set";
        if (source.getSelection() == EffectiveProvenanceView.SourceSelection.CONVENTION) {
            selection += rootKind == EffectiveProvenanceView.RootKind.CAPTURED ? " (captured)" : " (live)";
        }
        switch (source.getKnowledge()) {
            case KNOWN:
                MutationOccurrence occurrence = java.util.Objects.requireNonNull(source.getOccurrence());
                if (occurrence.getOperation().getKind() == SemanticOperation.Kind.UNCLASSIFIED_BINDING) {
                    selection += " (unclassified binding)";
                }
                frame(result, selection, occurrence.getAttribution());
                break;
            case DEFAULT:
                result.append("    default (").append(source.getReason()).append(")\n");
                break;
            case UNCONFIGURED:
                result.append("    not configured\n");
                break;
            case UNATTRIBUTED:
                result.append("    ").append(selection).append(" (unattributed)\n");
                break;
            case UNAVAILABLE:
                result.append("    ").append(selection).append(" (provenance unavailable: ").append(label(source.getReason())).append(")\n");
                break;
            default:
                throw new IllegalArgumentException("Unknown source knowledge.");
        }
    }

    private void frame(StringBuilder result, String operation, @Nullable Attribution attribution) {
        result.append("    ").append(operation).append(" by ");
        if (attribution == null) {
            result.append("unknown caller");
        } else {
            DiagnosticOrigin origin = attribution.getOrigin();
            switch (origin.getKind()) {
                case PLUGIN_ID:
                case PLUGIN_CLASS:
                    result.append("plugin '").append(label(origin.getIdentifier())).append("'");
                    break;
                case PROJECT_SCRIPT:
                    script(result, attribution, "build script");
                    break;
                case APPLIED_SCRIPT:
                    script(result, attribution, "applied script");
                    break;
                case SETTINGS_SCRIPT:
                    script(result, attribution, "settings script");
                    break;
                case INIT_SCRIPT:
                    script(result, attribution, "init script");
                    break;
                case UNKNOWN:
                    result.append("unknown origin");
                    break;
                default:
                    result.append(label(origin.getDisplayName()));
            }
            SourceLocation location = attribution.getLocation();
            if (location != null) {
                result.append(" (").append(label(sourcePath.apply(attribution))).append(':').append(location.getLine()).append(')');
            }
            ScopeIdentity scope = attribution.getSourceScope();
            if (!scope.equals(owner)) {
                result.append(" [");
                if (!scope.getBuildIdentity().equals(owner.getBuildIdentity())) {
                    result.append("build '").append(label(scope.getBuildIdentity())).append("', ");
                }
                result.append("scope '").append(label(scope.getScopePath())).append("']");
            }
        }
        result.append('\n');
    }

    private static void script(StringBuilder result, Attribution attribution, String name) {
        // Without a source location, the original display name is the only script identifier.
        result.append(attribution.getLocation() == null ? label(attribution.getOrigin().getDisplayName()) : name);
    }

    private static String operationName(String name) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (character == '_') {
                upper = true;
            } else {
                result.append(upper ? character : Character.toLowerCase(character));
                upper = false;
            }
        }
        return result.toString();
    }

    /** Keep user-controlled descriptor labels on one bounded line. */
    private static String label(String value) {
        StringBuilder result = new StringBuilder(Math.min(value.length(), LABEL_LIMIT));
        int length = Math.min(value.length(), LABEL_LIMIT);
        for (int i = 0; i < length; i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default: result.append(Character.isISOControl(character) ? '?' : character);
            }
        }
        if (value.length() > length) {
            result.append("...");
        }
        return result.toString();
    }
}
