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
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructor;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructorArgument;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnField;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnFieldTypeParam;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethod;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodArgument;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodTypeParam;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClass;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClassTypeParam;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnLocalFieldConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.ConstructorFieldConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.FieldDeclarationConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.FinalFieldDeclarationConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopAssignOpConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopConditionConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopInitConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.IfConditionConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.LambdaConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.StaticFinalFieldDeclarationConstant;
import org.gradle.internal.compiler.java.testclasses.constants.classtest.SwitchCaseConstant;

import java.util.Map;

@Annotation(AnnOnClass.ANN_ON_CLASS)
public class ReferenceImportTestClass<@Annotation(AnnOnClassTypeParam.ANN_ON_CLASS_TYPE_PARAM) T> {

    @Annotation(AnnConstantOnField.ANN_ON_FIELD)
    private Map<@Annotation(AnnConstantOnFieldTypeParam.ANN_ON_CONSTANT_FIELD_TYPE) String, String> field;

    public static final int STATIC_FINAL_FIELD = StaticFinalFieldDeclarationConstant.STATIC_FINAL_FIELD_DECLERATION;

    public final int finalField = FinalFieldDeclarationConstant.FINAL_FIELD_DECLARATION;

    private int fieldDecleration = FieldDeclarationConstant.FIELD_DECLARATION;

    @Annotation(AnnConstantOnConstructor.ANN_ON_CONSTRUCTOR)
    public ReferenceImportTestClass(@Annotation(AnnConstantOnConstructorArgument.ANN_ON_CONSTRUCTOR_ARG) String arg0) {
        @Annotation(AnnOnLocalFieldConstant.LOCAL_FIELD)
        int constructorField = ConstructorFieldConstant.CONSTRUCTOR_FIELD_CONSTANT;
    }

    @Annotation(AnnConstantOnMethod.ANN_ON_METHOD)
    <@Annotation(AnnConstantOnMethodTypeParam.ANN_CONSTANT_ON_METHOD_TYPE_PARAM) T> T methodBody(
        @Annotation(AnnConstantOnMethodArgument.ANN_CONSTANT_ON_METHOD_ARGUMENT) String argument) {
        return null;
    }

    @Annotation(AnnConstantOnMethod.ANN_ON_METHOD)
    void methodBody2() {
        for (int i = ForLoopInitConstant.FOR_LOOP_INIT; i < ForLoopConditionConstant.COND; i += ForLoopAssignOpConstant.ASSIGN_OP) {
            @Annotation(AnnOnLocalFieldConstant.LOCAL_FIELD)
            Runnable run = () -> System.out.println(LambdaConstant.LAMBDA);
            int value = 1;
            if (value == IfConditionConstant.IF_CONDITION) {
                value = 2;
            }
            switch (value) {
                case SwitchCaseConstant.SWITCH_CASE:
                default:
            }
        }
    }

}
