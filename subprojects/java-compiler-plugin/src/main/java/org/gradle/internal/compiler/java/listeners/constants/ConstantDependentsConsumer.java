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

package org.gradle.internal.compiler.java.listeners.constants;

import java.util.function.BiConsumer;

public class ConstantDependentsConsumer {

    private final BiConsumer<String, String> publicDependentDelegate;
    private final BiConsumer<String, String> privateDependentDelegate;

    public ConstantDependentsConsumer(BiConsumer<String, String> publicDependentDelegate, BiConsumer<String, String> privateDependentDelegate) {
        this.publicDependentDelegate = publicDependentDelegate;
        this.privateDependentDelegate = privateDependentDelegate;
    }

    /**
     * Consume "public" dependents of a constant. Private dependents in this context
     * are dependents that have a constant calculated from constant from origin.
     *
     * Example of public dependent:
     * class A {
     *     public static final int CALCULATE_PUBLIC_CONSTANT = CONSTANT;
     * }
     */
    public void consumePublicDependent(String constantOrigin, String constantDependent) {
        publicDependentDelegate.accept(constantOrigin, constantDependent);
    }

    /**
     * Consume "private" dependents of a constant.
     *
     * Example of private constant dependent:
     * class A {
     *     public static int method() {
     *         return CONSTANT;
     *     }
     * }
     */
    public void consumePrivateDependent(String constantOrigin, String constantDependent) {
        privateDependentDelegate.accept(constantOrigin, constantDependent);
    }

}
