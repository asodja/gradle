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

import static org.gradle.internal.compiler.java.testclasses.ReferenceConstantFirstTestClass.FIRST_REFERENCE_CONSTANT;

public class ReferenceConstantSecondTestClass {

    public static final int SECOND_REFERENCE_CONSTANT = FIRST_REFERENCE_CONSTANT;

}
