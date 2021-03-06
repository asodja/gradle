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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Int2ObjectMapSerializer;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class CompilerApiData {

    private final boolean isAvailable;
    private final Map<Integer, Set<String>> constantToClassMapping;

    public CompilerApiData() {
        this.constantToClassMapping = Collections.emptyMap();
        this.isAvailable = false;
    }

    public CompilerApiData(Map<Integer, Set<String>> classToConstantsMapping) {
        this.isAvailable = true;
        this.constantToClassMapping = classToConstantsMapping;
    }

    public Map<Integer, Set<String>> getConstantToClassMapping() {
        return constantToClassMapping;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public static final class Serializer extends AbstractSerializer<CompilerApiData> {
        private final Int2ObjectMapSerializer<Set<String>> mapSerializer;

        public Serializer(StringInterner interner) {
            InterningStringSerializer stringSerializer = new InterningStringSerializer(interner);
            SetSerializer<String> stringSetSerializer = new SetSerializer<>(stringSerializer);
            mapSerializer = new Int2ObjectMapSerializer<>(stringSetSerializer);
        }

        @Override
        public CompilerApiData read(Decoder decoder) throws Exception {
            return new CompilerApiData(mapSerializer.read(decoder));
        }

        @Override
        public void write(Encoder encoder, CompilerApiData value) throws Exception {
            mapSerializer.write(encoder, value.getConstantToClassMapping());
        }
    }

}
