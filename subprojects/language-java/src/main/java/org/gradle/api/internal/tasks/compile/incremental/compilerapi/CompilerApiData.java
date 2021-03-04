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
import it.unimi.dsi.fastutil.ints.IntSets;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.compile.incremental.processing.AnnotationProcessingData;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResource;
import org.gradle.api.internal.tasks.compile.incremental.processing.GeneratedResourceSerializer;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.InterningStringSerializer;
import org.gradle.internal.serialize.MapSerializer;
import org.gradle.internal.serialize.SetSerializer;

import java.util.Map;
import java.util.Set;

public class CompilerApiData {

    private final Map<String, IntSet> classToConstantsMapping;

    public CompilerApiData(Map<String, IntSet> classToConstantsMapping) {
        this.classToConstantsMapping = classToConstantsMapping;
    }

    public Map<String, IntSet> getClassToConstantsMapping() {
        return classToConstantsMapping;
    }

    public static final class Serializer extends AbstractSerializer<CompilerApiData> {
        private final SetSerializer<String> typesSerializer;
        private final MapSerializer<String, Set<String>> generatedTypesSerializer;
        private final SetSerializer<GeneratedResource> resourcesSerializer;
        private final MapSerializer<String, Set<GeneratedResource>> generatedResourcesSerializer;

        public Serializer(StringInterner interner) {
            InterningStringSerializer stringSerializer = new InterningStringSerializer(interner);
            typesSerializer = new SetSerializer<>(stringSerializer);
            generatedTypesSerializer = new MapSerializer<>(stringSerializer, typesSerializer);

            GeneratedResourceSerializer resourceSerializer = new GeneratedResourceSerializer(stringSerializer);
            this.resourcesSerializer = new SetSerializer<>(resourceSerializer);
            this.generatedResourcesSerializer = new MapSerializer<>(stringSerializer, resourcesSerializer);
        }

        @Override
        public CompilerApiData read(Decoder decoder) throws Exception {
            Map<String, Set<String>> generatedTypes = generatedTypesSerializer.read(decoder);
            Set<String> aggregatedTypes = typesSerializer.read(decoder);
            Set<String> generatedTypesDependingOnAllOthers = typesSerializer.read(decoder);
            String fullRebuildCause = decoder.readNullableString();
            Map<String, Set<GeneratedResource>> generatedResources = generatedResourcesSerializer.read(decoder);
            Set<GeneratedResource> generatedResourcesDependingOnAllOthers = resourcesSerializer.read(decoder);

            return null; // new AnnotationProcessingData(generatedTypes, aggregatedTypes, generatedTypesDependingOnAllOthers, generatedResources, generatedResourcesDependingOnAllOthers, fullRebuildCause);
        }

        @Override
        public void write(Encoder encoder, CompilerApiData value) throws Exception {
//            generatedTypesSerializer.write(encoder, value.generatedTypesByOrigin);
//            typesSerializer.write(encoder, value.aggregatedTypes);
//            typesSerializer.write(encoder, value.generatedTypesDependingOnAllOthers);
//            encoder.writeNullableString(value.fullRebuildCause);
//            generatedResourcesSerializer.write(encoder, value.generatedResourcesByOrigin);
//            resourcesSerializer.write(encoder, value.generatedResourcesDependingOnAllOthers);
        }
    }

}
