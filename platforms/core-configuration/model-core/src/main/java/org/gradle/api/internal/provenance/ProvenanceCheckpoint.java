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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Descriptor-only transport of an ordinary provenance checkpoint.
 * The versioned format contains no executable code, property values or runtime attribution services.
 */
public final class ProvenanceCheckpoint {
    private static final int VERSION = 5;
    private final EffectiveProvenanceView view;
    @Nullable
    private final MutationOccurrence lastAcceptedMutation;

    public ProvenanceCheckpoint(EffectiveProvenanceView view, @Nullable MutationOccurrence lastAcceptedMutation) {
        this.view = view;
        this.lastAcceptedMutation = lastAcceptedMutation;
    }

    public EffectiveProvenanceView getView() {
        return view;
    }

    @Nullable
    public MutationOccurrence getLastAcceptedMutation() {
        return lastAcceptedMutation;
    }

    /** Encodes all effective updates, independently of diagnostic rendering limits. */
    public byte[] encode() {
        return encode(0);
    }

    private byte[] encode(int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("Property provenance binding depth exceeded.");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            string(out, view.getTarget().getOwner().getBuildIdentity());
            string(out, view.getTarget().getOwner().getScopePath());
            string(out, view.getTarget().getModelPath());
            string(out, view.getRootKind().name());
            string(out, view.getSource().getSelection().name());
            string(out, view.getSource().getKnowledge().name());
            string(out, view.getSource().getReason());
            occurrence(out, view.getSource().getOccurrence());
            out.writeInt(view.getUpdates().size());
            for (MutationOccurrence update : view.getUpdates().inApplicationOrder()) {
                occurrence(out, update);
            }
            out.writeInt(view.getShadowedConfiguration().size());
            for (MutationOccurrence shadowed : view.getShadowedConfiguration()) {
                occurrence(out, shadowed);
            }
            out.writeInt(view.getPartialReasons().size());
            for (String reason : view.getPartialReasons()) {
                string(out, reason);
            }
            out.writeInt(view.getProviderOperations().size());
            for (ProviderOperation operation : view.getProviderOperations()) {
                string(out, operation.getKind().name());
                out.writeBoolean(operation.getAttribution() != null);
                if (operation.getAttribution() != null) {
                    attribution(out, operation.getAttribution());
                }
            }
            out.writeBoolean(view.getInput() != null);
            if (view.getInput() != null) {
                byte[] input = new ProvenanceCheckpoint(view.getInput(), null).encode(depth + 1);
                out.writeInt(input.length);
                out.write(input);
            }
            occurrence(out, lastAcceptedMutation);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Restores existing identities; decoding never allocates a mutation identity. */
    public static ProvenanceCheckpoint decode(byte[] bytes) {
        return decode(bytes, 0);
    }

    private static ProvenanceCheckpoint decode(byte[] bytes, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("Property provenance binding depth exceeded.");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != VERSION) {
                throw new IllegalArgumentException("Unsupported property provenance checkpoint version.");
            }
            TargetContext target = new TargetContext(new ScopeIdentity(string(in), string(in)), string(in));
            EffectiveProvenanceView.RootKind root = EffectiveProvenanceView.RootKind.valueOf(string(in));
            EffectiveProvenanceView.SourceSelection selection = EffectiveProvenanceView.SourceSelection.valueOf(string(in));
            EffectiveProvenanceView.SourceKnowledge knowledge = EffectiveProvenanceView.SourceKnowledge.valueOf(string(in));
            String reason = string(in);
            MutationOccurrence selected = occurrence(in);
            EffectiveProvenanceView.Source source;
            switch (knowledge) {
                case KNOWN: source = EffectiveProvenanceView.Source.known(selection, java.util.Objects.requireNonNull(selected)); break;
                case UNATTRIBUTED: source = EffectiveProvenanceView.Source.unattributed(selection); break;
                case UNCONFIGURED: source = EffectiveProvenanceView.Source.unconfigured(); break;
                case DEFAULT: source = EffectiveProvenanceView.Source.collectionDefault(reason.equals("missing collection")); break;
                case UNAVAILABLE: source = EffectiveProvenanceView.Source.unavailable(selection, reason); break;
                default: throw new IllegalArgumentException("Unknown source knowledge.");
            }
            UpdateSequence updates = UpdateSequence.empty();
            int updateCount = count(in);
            for (int i = 0; i < updateCount; i++) {
                updates = updates.append(java.util.Objects.requireNonNull(occurrence(in)));
            }
            List<MutationOccurrence> shadowed = new ArrayList<>();
            int shadowedCount = count(in);
            for (int i = 0; i < shadowedCount; i++) {
                shadowed.add(java.util.Objects.requireNonNull(occurrence(in)));
            }
            List<String> reasons = new ArrayList<>();
            int reasonCount = count(in);
            for (int i = 0; i < reasonCount; i++) {
                reasons.add(string(in));
            }
            List<ProviderOperation> boundaries = new ArrayList<>();
            int boundaryCount = count(in);
            for (int i = 0; i < boundaryCount; i++) {
                EffectiveProvenanceView.ProviderBoundary kind = EffectiveProvenanceView.ProviderBoundary.valueOf(string(in));
                boundaries.add(new ProviderOperation(kind, in.readBoolean() ? attribution(in) : null));
            }
            EffectiveProvenanceView input = null;
            if (in.readBoolean()) {
                int length = count(in);
                if (length > in.available()) {
                    throw new IllegalArgumentException("Invalid input provenance length.");
                }
                byte[] nested = new byte[length];
                in.readFully(nested);
                input = decode(nested, depth + 1).getView();
            }
            ProvenanceCheckpoint result = new ProvenanceCheckpoint(new EffectiveProvenanceView(target, root, source, updates, shadowed, reasons, boundaries, input), occurrence(in));
            if (in.available() != 0) {
                throw new IllegalArgumentException("Trailing property provenance checkpoint data.");
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid property provenance checkpoint.", e);
        }
    }

    private static void occurrence(DataOutputStream out, @Nullable MutationOccurrence value) throws IOException {
        out.writeBoolean(value != null);
        if (value == null) {
            return;
        }
        string(out, value.getScope());
        out.writeLong(value.getSequence());
        attribution(out, value.getAttribution());
        string(out, value.getOperation().getKind().name());
        string(out, value.getOperation().getReason());
        out.writeInt(value.getOperation().getShapes().size());
        for (SemanticOperation.Shape shape : value.getOperation().getShapes()) {
            string(out, shape.name());
        }
    }

    @Nullable
    private static MutationOccurrence occurrence(DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        String scope = string(in);
        long sequence = in.readLong();
        Attribution attribution = attribution(in);
        SemanticOperation.Kind kind = SemanticOperation.Kind.valueOf(string(in));
        String reason = string(in);
        SemanticOperation.Shape[] shapes = new SemanticOperation.Shape[count(in)];
        for (int i = 0; i < shapes.length; i++) {
            shapes[i] = SemanticOperation.Shape.valueOf(string(in));
        }
        SemanticOperation operation;
        switch (kind) {
            case EXPLICIT_BINDING: operation = SemanticOperation.EXPLICIT_BINDING; break;
            case CONVENTION_BINDING: operation = SemanticOperation.CONVENTION_BINDING; break;
            case CLEAR_EXPLICIT: operation = SemanticOperation.CLEAR_EXPLICIT; break;
            case CLEAR_CONVENTION: operation = SemanticOperation.CLEAR_CONVENTION; break;
            case PROMOTE_CONVENTION: operation = SemanticOperation.PROMOTE_CONVENTION; break;
            case UPDATE: operation = SemanticOperation.update(shapes); break;
            case CONTRIBUTION: operation = SemanticOperation.contribution(shapes[0]); break;
            case UNCLASSIFIED_BINDING: operation = SemanticOperation.unclassifiedBinding(reason); break;
            default: throw new IllegalArgumentException("Unknown semantic operation.");
        }
        return new MutationOccurrence(scope, sequence, attribution, operation);
    }

    private static void attribution(DataOutputStream out, Attribution attribution) throws IOException {
        string(out, attribution.getContributor().getDomain());
        string(out, attribution.getContributor().getKind().name());
        string(out, attribution.getContributor().getIdentity());
        string(out, attribution.getOrigin().getKind().name());
        string(out, attribution.getOrigin().getIdentifier());
        string(out, attribution.getOrigin().getDisplayName());
        string(out, attribution.getSourceScope().getBuildIdentity());
        string(out, attribution.getSourceScope().getScopePath());
        String token = attribution.getApplicationToken();
        out.writeBoolean(token != null);
        if (token != null) {
            string(out, token);
        }
        SourceLocation location = attribution.getLocation();
        out.writeBoolean(location != null);
        if (location != null) {
            string(out, location.getFileName());
            out.writeInt(location.getLine());
            out.writeBoolean(location.getPath() != null);
            if (location.getPath() != null) {
                string(out, location.getPath());
            }
        }
    }

    private static Attribution attribution(DataInputStream in) throws IOException {
        ContributorKey contributor = new ContributorKey(string(in), ContributorKey.Kind.valueOf(string(in)), string(in));
        DiagnosticOrigin origin = new DiagnosticOrigin(DiagnosticOrigin.Kind.valueOf(string(in)), string(in), string(in));
        ScopeIdentity sourceScope = new ScopeIdentity(string(in), string(in));
        Attribution attribution = new Attribution(contributor, origin, sourceScope, in.readBoolean() ? string(in) : null);
        if (in.readBoolean()) {
            attribution = attribution.withLocation(new SourceLocation(string(in), in.readInt(), in.readBoolean() ? string(in) : null));
        }
        return attribution;
    }

    private static void string(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String string(DataInputStream in) throws IOException {
        byte[] bytes = new byte[count(in)];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int count(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > in.available()) {
            throw new IllegalArgumentException("Invalid property provenance checkpoint length.");
        }
        return count;
    }
}
