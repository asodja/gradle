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

package com.gradle.internal.compiler.java

import org.gradle.internal.compiler.java.testclasses.compiler.TestCompiler
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

import static org.assertj.core.api.Assertions.assertThat

class ConstantsCollectorTest extends Specification {

    TestCompiler compiler
    Map<String, Collection<String>> collectedConstants

    def setup() {
        collectedConstants = [:]
        compiler = new TestCompiler(
            { f -> Optional.empty() },
            {},
            { m -> collectedConstants += m })
    }

    def "collect all statically imported constants"() {
        given:
        String clazz = loadClassToString("StaticImportTestClass.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.StaticImportTestClass", clazz)

        then:
        println collectedConstants
        collectedConstants.size() == 1
        collectedConstants.containsKey("org.gradle.internal.compiler.java.testclasses.StaticImportTestClass")
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.StaticImportTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopAssignOpConstant|ASSIGN_OP",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClass|ANN_ON_CLASS",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructor|ANN_ON_CONSTRUCTOR",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructorArgument|ANN_ON_CONSTRUCTOR_ARG",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FinalFieldDeclarationConstant|FINAL_FIELD_DECLARATION",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FieldDeclarationConstant|FIELD_DECLARATION",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClassTypeParam|ANN_ON_CLASS_TYPE_PARAM",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodArgument|ANN_CONSTANT_ON_METHOD_ARGUMENT",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopConditionConstant|COND",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnLocalFieldConstant|LOCAL_FIELD",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.LambdaConstant|LAMBDA",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.StaticFinalFieldDeclarationConstant|STATIC_FINAL_FIELD_DECLERATION",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodTypeParam|ANN_CONSTANT_ON_METHOD_TYPE_PARAM",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.IfConditionConstant|IF_CONDITION",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnFieldTypeParam|ANN_ON_CONSTANT_FIELD_TYPE",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnField|ANN_ON_FIELD",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.SwitchCaseConstant|SWITCH_CASE",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ConstructorFieldConstant|CONSTRUCTOR_FIELD_CONSTANT",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethod|ANN_ON_METHOD",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopInitConstant|FOR_LOOP_INIT"
        )
    }

    def "collect all statically imported constants for Annotation class"() {
        given:
        String clazz = loadClassToString("AnnotationTestClass.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.AnnotationTestClass", clazz)

        then:
        collectedConstants.size() == 1
        collectedConstants.containsKey("org.gradle.internal.compiler.java.testclasses.AnnotationTestClass")
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.AnnotationTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultArrayValue|CONSTANT_ON_DEFAULT_ARRAY_VALUE",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultValue|CONSTANT_ON_DEFAULT_VALUE",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnClassAnnotation|CONSTANT_ON_CLASS_ANNOTATION",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnValueAnnotation|CONSTANT_ON_VALUE_ANNOTATION"
        )
    }

    def "collect all statically imported constants for package-info class"() {
        given:
        String clazz = loadClassToString("packageinfo/package-info.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.packageinfo.package-info", clazz)

        then:
        collectedConstants.size() == 1
        collectedConstants.keySet().first() == "org.gradle.internal.compiler.java.testclasses.packageinfo"
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.packageinfo.PackageInfoConstant"
        )
    }

    static String loadClassToString(String className) {
        String workspaceDir = new File("").absolutePath
        String testSources = "src${File.separator}test${File.separator}java"
        String testClassPackageDir = "org${File.separator}gradle${File.separator}internal${File.separator}compiler${File.separator}java${File.separator}testclasses"
        String classLocation = "${workspaceDir}${File.separator}${testSources}${File.separator}${testClassPackageDir}${File.separator}${className}"
        return new String(Files.readAllBytes(Paths.get(classLocation)), StandardCharsets.UTF_8)
    }

}
