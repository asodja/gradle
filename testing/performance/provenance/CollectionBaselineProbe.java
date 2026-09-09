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
import org.gradle.api.internal.provider.PropertyHost;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;

/** Matched collection engine workloads; large contribution chains are constructed without evaluating them. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class CollectionBaselineProbe {
    protected DefaultPropertyFactory factory() {
        return new DefaultPropertyFactory(PropertyHost.NO_OP);
    }

    protected AbstractProperty create(String kind) {
        DefaultPropertyFactory factory = factory();
        switch (kind) {
            case "list": return factory.listProperty(String.class);
            case "set": return factory.setProperty(String.class);
            default: return factory.mapProperty(String.class, String.class);
        }
    }

    static void bind(AbstractProperty property) {
        if (property instanceof DefaultMapProperty) {
            ((DefaultMapProperty) property).set(Map.of("root", "root"));
        } else {
            ((AbstractCollectionProperty) property).set(List.of("root"));
        }
    }

    static void contribute(AbstractProperty property) {
        if (property instanceof DefaultMapProperty) {
            ((DefaultMapProperty) property).put("entry", "entry");
        } else {
            ((AbstractCollectionProperty) property).add("entry");
        }
    }

    public static void main(String[] args) throws Exception {
        new CollectionBaselineProbe().run();
    }

    protected void run() throws Exception {
        for (String kind : new String[]{"list", "set", "map"}) {
            AbstractProperty property = create(kind);
            ProvenanceAllocationProbe.layout(kind + " property", property);
            bind(property);
            ProvenanceAllocationProbe.measure(kind + " binding", 10000, () -> bind(property));
            ProvenanceAllocationProbe.measure(kind + " copy", 10000, () -> ProvenanceAllocationProbe.consume(property.shallowCopy()));
            ProvenanceAllocationProbe.measure(kind + " contribution and reset", 10000, () -> {
                contribute(property);
                bind(property);
            });
            ProvenanceAllocationProbe.measure(kind + " construct bind finalize", 1000, () -> {
                AbstractProperty next = create(kind);
                bind(next);
                contribute(next);
                next.finalizeValue();
                ProvenanceAllocationProbe.consume(next);
            });
            for (int count : new int[]{0, 1, 8, 128, 4096}) {
                ProvenanceAllocationProbe.measure(kind + " construct chain " + count, Math.max(10, 10000 / Math.max(1, count)), () -> {
                    AbstractProperty next = create(kind);
                    bind(next);
                    for (int i = 0; i < count; i++) {
                        contribute(next);
                    }
                    ProvenanceAllocationProbe.consume(next);
                });
            }
            for (String shape : new String[]{"empty", "populated", "finalized"}) {
                WeakReference<?> owner = snapshotOwner(kind, shape);
                for (int i = 0; i < 5; i++) {
                    System.gc();
                }
                System.out.println("{\"engineSnapshot\":\"" + kind + " " + shape + "\",\"ownerRetained\":" + (owner.get() != null) + "}");
            }
        }
    }

    private WeakReference<?> snapshotOwner(String kind, String shape) {
        AbstractProperty property = create(kind);
        if (!shape.equals("empty")) {
            bind(property);
        }
        if (shape.equals("finalized")) {
            property.finalizeValue();
        }
        ProvenanceAllocationProbe.consume(property.shallowCopy());
        return new WeakReference<>(property);
    }

}
