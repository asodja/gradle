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

package org.gradle.internal.compiler.java.testclasses;

import static org.gradle.internal.compiler.java.testclasses.InnerClassTestClass.InnerConstantClass.CONSTANT_IN_INNER_CLASS;
import static org.gradle.internal.compiler.java.testclasses.InnerClassTestClass.InnerStaticConstantClass.CONSTANT_IN_STATIC_INNER_CLASS;
import static org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.AnonymousClassConstant.ANONYMOUS_CLASS_CONSTANT;
import static org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.InnerClassConstant.INNER_CLASS_CONSTANT;
import static org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.InnerStaticClassConstant.INNER_STATIC_CLASS_CONSTANT;

public class InnerClassTestClass {

    @Override
    public int hashCode() {
        return 5;
    }

    void method() {
        @SuppressWarnings("Convert2Lambda")
        Runnable annonymousClass = new Runnable() {
            @Override
            public void run() {
                int x = ANONYMOUS_CLASS_CONSTANT;
                int y = CONSTANT_IN_INNER_CLASS;
                int z = CONSTANT_IN_STATIC_INNER_CLASS;
            }
        };
    }

    public class InnerClass {
        int foo() {
            return INNER_CLASS_CONSTANT;
        }
        int bar() {
            return CONSTANT_IN_INNER_CLASS;
        }
        int foobar() {
            return CONSTANT_IN_STATIC_INNER_CLASS;
        }
    }

    public static class InnerStaticClass {
        int foo() {
            return INNER_STATIC_CLASS_CONSTANT;
        }
        int bar() {
            return CONSTANT_IN_INNER_CLASS;
        }
        int foobar() {
            return CONSTANT_IN_STATIC_INNER_CLASS;
        }
    }

    public class InnerConstantClass {
        public static final int CONSTANT_IN_INNER_CLASS = 1;
    }

    public static class InnerStaticConstantClass {
        public static final int CONSTANT_IN_STATIC_INNER_CLASS = 1;
    }

}
