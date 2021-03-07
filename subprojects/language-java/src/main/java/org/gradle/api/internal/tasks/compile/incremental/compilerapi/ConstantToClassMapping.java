/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi;

import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Int2ObjectMapSerializer;
import org.gradle.internal.serialize.IntSetSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.ListSerializer;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@NonNullApi
public class ConstantToClassMapping {

    /**
     * A list of all class names. Can access class name by index located in constantToClassIndexes
     * List is used for low memory-foot-print
     **/
    private final List<String> classNames;
    private final Map<Integer, IntSet> constantToClassIndexes;

    public ConstantToClassMapping(List<String> classNames,
                                  Map<Integer, IntSet> constantToClassIndexes) {
        this.classNames = classNames;
        this.constantToClassIndexes = constantToClassIndexes;
    }

    public List<String> getClassNames() {
        return classNames;
    }

    public Map<Integer, IntSet> getConstantToClassIndexes() {
        return constantToClassIndexes;
    }

    public Set<String> getDependentClasses(int constantHash) {
        if (!constantToClassIndexes.containsKey(constantHash)) {
            return Collections.emptySet();
        }
        IntSet classIndexes = constantToClassIndexes.get(constantHash);
        Set<String> dependents = new ObjectOpenHashSet<>(classIndexes.size());
        for (int i : classIndexes) {
            dependents.add(classNames.get(i));
        }
        return dependents;
    }

    public static final class Serializer extends AbstractSerializer<ConstantToClassMapping> {
        private final Int2ObjectMapSerializer<IntSet> mapSerializer;
        private final ListSerializer<String> classNamesSerializer;

        public Serializer(StringInterner interner) {
            InterningStringSerializer stringSerializer = new InterningStringSerializer(interner);
            classNamesSerializer = new ListSerializer<>(stringSerializer);
            mapSerializer = new Int2ObjectMapSerializer<>(IntSetSerializer.INSTANCE);
        }

        @Override
        public ConstantToClassMapping read(Decoder decoder) throws Exception {
            List<String> classNames = classNamesSerializer.read(decoder);
            Map<Integer, IntSet> constantToClassIndexes = mapSerializer.read(decoder);
            return new ConstantToClassMapping(classNames, constantToClassIndexes);
        }

        @Override
        public void write(Encoder encoder, ConstantToClassMapping value) throws Exception {
            classNamesSerializer.write(encoder, value.classNames);
            mapSerializer.write(encoder, value.constantToClassIndexes);
        }
    }

}
