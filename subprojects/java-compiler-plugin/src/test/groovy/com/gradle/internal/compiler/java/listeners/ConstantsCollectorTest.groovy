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

package com.gradle.internal.compiler.java.listeners

import org.gradle.internal.compiler.java.testclasses.compiler.TestCompiler
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Paths

import static org.assertj.core.api.Assertions.assertThat

class ConstantsCollectorTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    TestCompiler compiler
    Map<String, Collection<String>> collectedConstants

    def setup() {
        collectedConstants = [:]
        compiler = new TestCompiler(
            temporaryFolder.newFolder(),
            { f -> Optional.empty() },
            {},
            { m -> collectedConstants += m })
    }

    def "collect all constants from class"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.ReferenceImportTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        def classes = collectedConstants[ "org.gradle.internal.compiler.java.testclasses.ReferenceImportTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethod",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructorArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClassTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopInitConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnField",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.SwitchCaseConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.StaticFinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnFieldTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ConstructorFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.IfConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructor",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClass",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnLocalFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopAssignOpConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.LambdaConstant"
        )
    }

    def "collect all constants in binary expresion from class"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.ExpressionTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.ExpressionTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethod",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructorArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClassTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopInitConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnField",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.SwitchCaseConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.StaticFinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnFieldTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ConstructorFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.IfConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructor",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClass",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnLocalFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopAssignOpConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.LambdaConstant"
        )
    }

    def "collect all statically imported constants from class"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.StaticImportTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.StaticImportTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethod",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructorArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClassTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopInitConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnField",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.SwitchCaseConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnMethodArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.StaticFinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnFieldTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ConstructorFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.FieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.IfConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnConstantOnConstructor",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnClass",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.AnnOnLocalFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.ForLoopAssignOpConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.classtest.LambdaConstant"
        )
    }

    def "collect all statically imported constants for Annotation class"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.AnnotationTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.AnnotationTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultArrayValue",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnClassAnnotation",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultValue",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnValueAnnotation"
        )
    }

    def "collect constants for chained constants reference"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.ReferenceConstantThirdTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.ReferenceConstantThirdTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.ReferenceConstantSecondTestClass"
        )
    }

    def "collect constants for inner classes"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.InnerClassTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        assertThat collectedConstants["org.gradle.internal.compiler.java.testclasses.InnerClassTestClass"].isEmpty()
        assertThat collectedConstants["org.gradle.internal.compiler.java.testclasses.InnerClassTestClass\$1"] containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.AnonymousClassConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.ClassWithInnerConstants\$InnerConstantClass",
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.ClassWithInnerConstants\$InnerStaticConstantClass"
        )
        assertThat collectedConstants["org.gradle.internal.compiler.java.testclasses.InnerClassTestClass\$InnerClass"] containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.InnerClassConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.ClassWithInnerConstants\$InnerConstantClass",
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.ClassWithInnerConstants\$InnerStaticConstantClass"
        )
        assertThat collectedConstants["org.gradle.internal.compiler.java.testclasses.InnerClassTestClass\$InnerStaticClass"] containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.InnerStaticClassConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.ClassWithInnerConstants\$InnerConstantClass",
            "org.gradle.internal.compiler.java.testclasses.constants.innerclasstest.ClassWithInnerConstants\$InnerStaticConstantClass"
        )
    }

    def "should not return self as a constant origin class"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.SelfReferenceTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        collectedConstants["org.gradle.internal.compiler.java.testclasses.SelfReferenceTestClass"].isEmpty()
    }

    def "should not collect non-primitive constants"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.NonPrimitiveConstantTestClass")

        when:
        compiler.compile(sourceFile)

        then:
        collectedConstants["org.gradle.internal.compiler.java.testclasses.NonPrimitiveConstantTestClass"].isEmpty()
    }

    @Requires({ javaVersion >= 12 })
    def "collect all statically imported constants for package-info class"() {
        given:
        File sourceFile = findSourceFile( "org.gradle.internal.compiler.java.testclasses.packageinfo.package-info")

        when:
        compiler.compile(sourceFile)

        then:
        assertThat collectedConstants["org.gradle.internal.compiler.java.testclasses.packageinfo"] containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.packageinfo.PackageInfoConstant"
        )
    }

    static File findSourceFile(String className) {
        String workspaceDir = new File("").absolutePath
        String testSources = ["src", "test", "java"].join(File.separator)
        String testClassPackageDir = className.replaceAll("[.]", File.separator)
        String absoluteClassFilePath = [workspaceDir, testSources, testClassPackageDir].join(File.separator) + ".java"
        return Paths.get(absoluteClassFilePath).toFile()
    }

}
