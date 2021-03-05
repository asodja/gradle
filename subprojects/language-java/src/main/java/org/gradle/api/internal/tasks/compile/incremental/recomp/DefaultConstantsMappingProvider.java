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

import com.google.common.base.Suppliers;
import com.google.common.collect.Multimap;
import org.gradle.api.internal.tasks.compile.ConstantsMappingFileAccessor;

import java.io.File;
import java.util.Collection;

public class DefaultConstantsMappingProvider implements ConstantsMappingProvider {

    private final com.google.common.base.Supplier<Multimap<String, String>> classToConstantsMapping;

    public DefaultConstantsMappingProvider(File mappingFile) {
        this.classToConstantsMapping = Suppliers.memoize(() -> ConstantsMappingFileAccessor.readConstantsClassesMappingFile(mappingFile));
    }

    @Override
    public Multimap<String, String> getClassToConstantsMapping() {
        return classToConstantsMapping.get();
    }

    @Override
    public boolean isConstantAnalysisEnabled() {
        return true;
    }

}
