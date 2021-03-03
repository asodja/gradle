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

import java.util.Collection;

public class GroovyConstantsMappingProvider implements ConstantsMappingProvider {

    @Override
    public Collection<String> getConstantsForClass(String className) {
        throw new UnsupportedOperationException("Get constants for class not supported.");
    }

    @Override
    public Collection<String> getClassesAccessingConstant(String constant) {
        throw new UnsupportedOperationException("Get classes for constants not supported.");
    }

    @Override
    public boolean isConstantAnalysisEnabled() {
        return false;
    }

}
