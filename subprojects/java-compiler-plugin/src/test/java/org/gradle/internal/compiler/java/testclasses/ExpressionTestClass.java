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

import java.util.Map;

import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructor.ANN_ON_CONSTRUCTOR;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructorArgument.ANN_ON_CONSTRUCTOR_ARG;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnField.ANN_ON_FIELD;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnFieldTypeParam.ANN_ON_CONSTANT_FIELD_TYPE;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethod.ANN_ON_METHOD;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodArgument.ANN_CONSTANT_ON_METHOD_ARGUMENT;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodTypeParam.ANN_CONSTANT_ON_METHOD_TYPE_PARAM;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClass.ANN_ON_CLASS;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClassTypeParam.ANN_ON_CLASS_TYPE_PARAM;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnLocalFieldConstant.LOCAL_FIELD;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.ConstructorFieldConstant.CONSTRUCTOR_FIELD_CONSTANT;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.FieldDeclarationConstant.FIELD_DECLARATION;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.FinalFieldDeclarationConstant.FINAL_FIELD_DECLARATION;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopAssignOpConstant.ASSIGN_OP;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopConditionConstant.COND;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopInitConstant.FOR_LOOP_INIT;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.IfConditionConstant.IF_CONDITION;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.LambdaConstant.LAMBDA;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.StaticFinalFieldDeclarationConstant.STATIC_FINAL_FIELD_DECLERATION;
import static org.gradle.internal.compiler.java.testclasses.constants.classtest.SwitchCaseConstant.SWITCH_CASE;

@Annotation(ANN_ON_CLASS + 1)
class ExpressionTestClass<@Annotation(ANN_ON_CLASS_TYPE_PARAM + 1) T> {

    @Annotation(ANN_ON_FIELD)
    private Map<@Annotation(ANN_ON_CONSTANT_FIELD_TYPE + 1) String, String> field;

    public static final int STATIC_FINAL_FIELD = STATIC_FINAL_FIELD_DECLERATION + 1;

    public final int finalField = FINAL_FIELD_DECLARATION + 1;

    private int fieldDecleration = FIELD_DECLARATION + 1;

    @Annotation(ANN_ON_CONSTRUCTOR + 1)
    public ExpressionTestClass(@Annotation(ANN_ON_CONSTRUCTOR_ARG + 1) String arg0) {
        @Annotation(LOCAL_FIELD + 1)
        int constructorField = CONSTRUCTOR_FIELD_CONSTANT + 1;
    }

    @Annotation(ANN_ON_METHOD + 1)
    <@Annotation(ANN_CONSTANT_ON_METHOD_TYPE_PARAM + 1) T> T methodBody(
        @Annotation(ANN_CONSTANT_ON_METHOD_ARGUMENT + 1) String argument) {
        return null;
    }

    @Annotation(ANN_ON_METHOD + 1)
    void methodBody2() {
        for (int i = FOR_LOOP_INIT + 1; i < COND + 1; i += ASSIGN_OP + 1) {
            @Annotation(LOCAL_FIELD + 1)
            Runnable run = () -> System.out.println(LAMBDA + 1);
            int value = 1;
            if (value == IF_CONDITION + 1) {
                value = 2;
            }
            switch (value) {
                case SWITCH_CASE + 1:
                default:
            }
        }
    }

}
