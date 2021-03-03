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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.Collection;

public class DefaultConstantsMappingProvider implements ConstantsMappingProvider {
    private final Multimap<String, String> classToConstantsMapping;
    private final Multimap<String, String> constantToClassesSourceMapping;

    public DefaultConstantsMappingProvider(Multimap<String, String> classToConstantsMapping) {
        this.classToConstantsMapping = classToConstantsMapping;
        this.constantToClassesSourceMapping = constructReverseMapping(classToConstantsMapping);
    }

    private Multimap<String, String> constructReverseMapping(Multimap<String, String> classToConstantsMapping) {
        Multimap<String, String> constantToClassesSourceMapping = HashMultimap.create();
        classToConstantsMapping.entries().forEach(e -> constantToClassesSourceMapping.put(e.getValue(), e.getKey()));
        return constantToClassesSourceMapping;
    }

    @Override
    public Collection<String> getConstantsForClass(String className) {
        return classToConstantsMapping.get(className);
    }

    @Override
    public Collection<String> getClassesAccessingConstant(String constants) {
        return constantToClassesSourceMapping.get(constants);
    }

    @Override
    public boolean isConstantAnalysisEnabled() {
        return true;
    }

}
