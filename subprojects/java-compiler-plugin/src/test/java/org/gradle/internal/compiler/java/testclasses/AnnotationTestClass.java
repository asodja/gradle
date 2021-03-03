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

import org.gradle.internal.compiler.java.testclasses.annotations.Annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnClassAnnotation.CONSTANT_ON_CLASS_ANNOTATION;
import static org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultArrayValue.CONSTANT_ON_DEFAULT_ARRAY_VALUE;
import static org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultValue.CONSTANT_ON_DEFAULT_VALUE;
import static org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnValueAnnotation.CONSTANT_ON_VALUE_ANNOTATION;

@Annotation(value = CONSTANT_ON_CLASS_ANNOTATION + 1)
@Retention(RetentionPolicy.RUNTIME)
public @interface AnnotationTestClass {

    @Annotation(value = CONSTANT_ON_VALUE_ANNOTATION + 1)
    int value() default CONSTANT_ON_DEFAULT_VALUE + 1;

    int[] values() default { CONSTANT_ON_DEFAULT_ARRAY_VALUE + 1};

}
