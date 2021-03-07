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

package org.gradle.api.internal.tasks.compile.incremental.compilerapi.constants;

import com.google.common.collect.Multimap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.ConstantToClassMapping;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.gradle.api.internal.tasks.compile.ConstantsMappingFileAccessor.readConstantsClassesMappingFile;

/**
 * Class used to merge new constants mapping from compiler api results with old results
 *
 * It first transforms old mappings to: Map[String, IntSet] where key is class name and value are all constants that are used in that class
 */
@NonNullApi
public class ConstantToClassMappingMerger {

    public ConstantToClassMapping merge(File compilationClassToConstantsFile, @Nullable ConstantToClassMapping oldMapping, Set<String> removedClasses) {
        Multimap<String, String> mapping = readConstantsClassesMappingFile(compilationClassToConstantsFile);
        if (oldMapping == null) {
            return doMerge(mapping, new ConstantToClassMapping(Collections.emptyList(), Collections.emptyMap()), removedClasses);
        } else {
            return doMerge(mapping, oldMapping, removedClasses);
        }
    }

    private ConstantToClassMapping doMerge(Multimap<String, String> newMapping, ConstantToClassMapping oldMapping, Set<String> removedClasses) {
        // Convert previous data to similar format as we get it from compiler api
        Map<String, IntSet> classesToConstants = toClassToConstantsMapping(oldMapping);
        // Update it
        updateClassToConstantsMapping(classesToConstants, newMapping, removedClasses);
        // Convert back to "memory efficient" format
        return toConstantsToClassMapping(classesToConstants);
    }

    private Map<String, IntSet> toClassToConstantsMapping(ConstantToClassMapping constantToClassMapping) {
        Map<String, IntSet> classToConstants = new Object2ObjectOpenHashMap<>();
        List<String> classNames = constantToClassMapping.getClassNames();
        constantToClassMapping.getConstantToClassIndexes().forEach((constantOrigins, classes) -> {
            for (int i : classes) {
                String className = classNames.get(i);
                classToConstants.computeIfAbsent(className, (k) -> new IntOpenHashSet()).add((int) constantOrigins);
            }
        });
        return classToConstants;
    }

    private void updateClassToConstantsMapping(Map<String, IntSet> classesToConstants, Multimap<String, String> newMapping, Set<String> removedClasses) {
        newMapping.asMap().forEach((clazz, constantOrigins) -> {
            if (constantOrigins.isEmpty() || removedClasses.contains(clazz)) {
                // constantOrigins.isEmpty() means that all referenced constants were removed from class
                classesToConstants.remove(clazz);
            } else {
                classesToConstants.put(clazz, toSetOfConstantOriginHashes(constantOrigins));
            }
        });
    }

    private IntSet toSetOfConstantOriginHashes(Collection<String> constantOrigins) {
        IntSet constantOriginHashes = new IntOpenHashSet();
        for (String constantOrigin : constantOrigins) {
            constantOriginHashes.add(constantOrigin.hashCode());
        }
        return constantOriginHashes;
    }

    private ConstantToClassMapping toConstantsToClassMapping(Map<String, IntSet> classesToConstants) {
        AtomicInteger currentIndex = new AtomicInteger();
        List<String> newClassNames = new ArrayList<>(classesToConstants.keySet().size());
        Map<Integer, IntSet> constantToClassIndexes = new Int2ObjectOpenHashMap<>();
        classesToConstants.forEach((clazz, constantOrigins) -> {
            newClassNames.add(clazz);
            for (int constantOriginHash : constantOrigins) {
                constantToClassIndexes.computeIfAbsent(constantOriginHash, (k) -> new IntOpenHashSet()).add(currentIndex.get());
            }
            currentIndex.incrementAndGet();
        });
        return new ConstantToClassMapping(newClassNames, constantToClassIndexes);
    }

}
