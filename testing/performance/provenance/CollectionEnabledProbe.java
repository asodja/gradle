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

import org.gradle.api.internal.provider.AbstractCollectionProperty;
import org.gradle.api.internal.provider.AbstractProperty;
import org.gradle.api.internal.provider.DefaultMapProperty;
import org.gradle.api.internal.provider.DefaultPropertyFactory;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.PropertyProvenanceHost;
import org.gradle.api.internal.provenance.Attribution;
import org.gradle.api.internal.provenance.ContributorKey;
import org.gradle.api.internal.provenance.DiagnosticOrigin;
import org.gradle.api.internal.provenance.ScopeIdentity;
import org.gradle.internal.state.ModelObject;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

/** Enabled collection allocation and retention with shared descriptors, without provider traversal. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class CollectionEnabledProbe extends CollectionBaselineProbe {
    private static final ScopeIdentity OWNER = new ScopeIdentity("build", ":project");
    private static final Attribution AUTHOR = new Attribution(new ContributorKey("domain", ContributorKey.Kind.PLUGIN_ID, "plugin"),
        new DiagnosticOrigin(DiagnosticOrigin.Kind.PLUGIN_ID, "plugin", "plugin"), OWNER, null);
    private final Host host = new Host();

    private static final class Host implements PropertyProvenanceHost {
        private int sequence;
        public ScopeIdentity getOwnerScope() { return OWNER; }
        public String newOccurrenceScope() { return "property/" + sequence++; }
        public Attribution currentAttribution() { return AUTHOR; }
        public String beforeRead(ModelObject producer) { return null; }
    }

    @Override
    protected DefaultPropertyFactory factory() {
        return new DefaultPropertyFactory(host);
    }

    public static void main(String[] args) throws Exception {
        new CollectionEnabledProbe().run();
        for (String kind : new String[]{"list", "set", "map"}) {
            AbstractProperty property = new CollectionEnabledProbe().create(kind);
            var field = property.getClass().getDeclaredField("provenance");
            field.setAccessible(true);
            Object adapter = field.get(property);
            ProvenanceAllocationProbe.layout(kind + " adapter", adapter);
            var state = adapter.getClass().getDeclaredField("state");
            state.setAccessible(true);
            ProvenanceAllocationProbe.layout(kind + " descriptor state", state.get(adapter));
            for (String scenario : new String[]{"view", "copy", "replacement", "finalization"}) {
                List<WeakReference<?>> refs = retention(kind, scenario);
                for (int i = 0; i < 5; i++) {
                    System.gc();
                }
                long retained = refs.stream().filter(ref -> ref.get() != null).count();
                System.out.println("{\"retention\":\"" + kind + " " + scenario + "\",\"unwantedReferences\":" + retained + "}");
                if (retained != 0) {
                    throw new AssertionError("Unexpected retention: " + kind + " " + scenario);
                }
            }
        }
    }

    private static List<WeakReference<?>> retention(String kind, String scenario) throws Exception {
        CollectionEnabledProbe probe = new CollectionEnabledProbe();
        AbstractProperty property = probe.create(kind);
        DefaultProvider provider = new DefaultProvider(() -> kind.equals("map") ? Map.of("root", "root") : List.of("root"));
        if (property instanceof DefaultMapProperty) {
            ((DefaultMapProperty) property).set(provider);
        } else {
            ((AbstractCollectionProperty) property).set(provider);
        }
        contribute(property);
        if (scenario.equals("view")) {
            ProvenanceAllocationProbe.consume(property.getClass().getMethod("getEffectiveProvenance").invoke(property));
            return List.of(new WeakReference<>(property), new WeakReference<>(provider), new WeakReference<>(probe.host));
        }
        if (scenario.equals("copy")) {
            Object copy = property.shallowCopy();
            bind(property);
            ProvenanceAllocationProbe.consume(copy);
            return List.of(new WeakReference<>(property), new WeakReference<>(probe.host));
        }
        if (scenario.equals("replacement")) {
            bind(property);
            ProvenanceAllocationProbe.consume(property);
            return List.of(new WeakReference<>(provider));
        }
        property.finalizeValue();
        ProvenanceAllocationProbe.consume(property);
        return List.of(new WeakReference<>(provider), new WeakReference<>(probe.host));
    }
}
