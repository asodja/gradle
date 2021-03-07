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
import spock.lang.Requires
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

import static org.assertj.core.api.Assertions.assertThat
import static org.spockframework.util.CollectionUtil.asSet

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
        assertThat collectedConstants.keySet() containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses",
            "org.gradle.internal.compiler.java.testclasses.StaticImportTestClass"
        )
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
        String clazz = loadClassToString("AnnotationTestClass.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.AnnotationTestClass", clazz)

        then:
        assertThat collectedConstants.keySet() containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses",
            "org.gradle.internal.compiler.java.testclasses.AnnotationTestClass"
        )
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.AnnotationTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultArrayValue",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnClassAnnotation",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnDefaultValue",
            "org.gradle.internal.compiler.java.testclasses.constants.annotationtest.ConstantOnValueAnnotation"
        )
    }

    @Requires({ javaVersion >= 12 })
    def "collect all statically imported constants for package-info class"() {
        given:
        String clazz = loadClassToString("packageinfo/package-info.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.packageinfo.package-info", clazz)

        then:
        collectedConstants.keySet() == asSet("org.gradle.internal.compiler.java.testclasses.packageinfo")
        assertThat collectedConstants["org.gradle.internal.compiler.java.testclasses.packageinfo"] containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.packageinfo.PackageInfoConstant"
        )
    }

    def "collect constants for chained constants reference"() {
        given:
        String clazz = loadClassToString("ReferenceConstantThirdTestClass.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.ReferenceConstantThirdTestClass", clazz)

        then:
        assertThat collectedConstants.keySet() containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses",
            "org.gradle.internal.compiler.java.testclasses.ReferenceConstantThirdTestClass"
        )
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.ReferenceConstantThirdTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.ReferenceConstantSecondTestClass"
        )
    }

    def "collect constants for inner classes"() {
        given:
        String clazz = loadClassToString("InnerClassTestClass.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.InnerClassTestClass", clazz)

        then:
        assertThat collectedConstants.keySet() containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses",
            "org.gradle.internal.compiler.java.testclasses.InnerClassTestClass",
            "org.gradle.internal.compiler.java.testclasses.InnerClassTestClass\$1",
            "org.gradle.internal.compiler.java.testclasses.InnerClassTestClass\$InnerClass",
            "org.gradle.internal.compiler.java.testclasses.InnerClassTestClass\$InnerStaticClass"
        )
        assertThat collectedConstants["org.gradle.internal.compiler.java.testclasses"].isEmpty()
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
        String clazz = loadClassToString("SelfReferenceTestClass.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.SelfReferenceTestClass", clazz)

        then:
        assertThat collectedConstants.keySet() containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses",
            "org.gradle.internal.compiler.java.testclasses.SelfReferenceTestClass"
        )
        collectedConstants["org.gradle.internal.compiler.java.testclasses.SelfReferenceTestClass"].isEmpty()
    }

    def "should not collect non-primitive constants"() {
        given:
        String clazz = loadClassToString("NonPrimitiveConstantTestClass.java")

        when:
        compiler.compile("org.gradle.internal.compiler.java.testclasses.NonPrimitiveConstantTestClass", clazz)

        then:
        assertThat collectedConstants.keySet() containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses",
            "org.gradle.internal.compiler.java.testclasses.NonPrimitiveConstantTestClass"
        )
        collectedConstants["org.gradle.internal.compiler.java.testclasses.NonPrimitiveConstantTestClass"].isEmpty()
    }


    static String loadClassToString(String className) {
        String workspaceDir = new File("").absolutePath
        String testSources = "src${File.separator}test${File.separator}java"
        String testClassPackageDir = "org${File.separator}gradle${File.separator}internal${File.separator}compiler${File.separator}java${File.separator}testclasses"
        String classLocation = "${workspaceDir}${File.separator}${testSources}${File.separator}${testClassPackageDir}${File.separator}${className}"
        return new String(Files.readAllBytes(Paths.get(classLocation)), StandardCharsets.UTF_8)
    }

}
