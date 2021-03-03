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

class IncrementalCompileTaskTest extends Specification {

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
        collectedConstants.size() == 1
        collectedConstants.containsKey("org.gradle.internal.compiler.java.testclasses.StaticImportTestClass")
        def classes = collectedConstants["org.gradle.internal.compiler.java.testclasses.StaticImportTestClass"]
        assertThat classes containsExactlyInAnyOrder(
            "org.gradle.internal.compiler.java.testclasses.constants.AnnOnClass",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnOnClassTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnConstantOnConstructorArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnConstantOnConstructor",
            "org.gradle.internal.compiler.java.testclasses.constants.SwitchCaseConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.ConstructorFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnConstantOnMethodArgument",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnConstantOnFieldTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.IfConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnConstantOnField",
            "org.gradle.internal.compiler.java.testclasses.constants.StaticFinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.FinalFieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.FieldDeclarationConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnOnLocalFieldConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnConstantOnMethod",
            "org.gradle.internal.compiler.java.testclasses.constants.AnnConstantOnMethodTypeParam",
            "org.gradle.internal.compiler.java.testclasses.constants.ForLoopInitConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.ForLoopConditionConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.ForLoopAssignOpConstant",
            "org.gradle.internal.compiler.java.testclasses.constants.LambdaConstant"
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
