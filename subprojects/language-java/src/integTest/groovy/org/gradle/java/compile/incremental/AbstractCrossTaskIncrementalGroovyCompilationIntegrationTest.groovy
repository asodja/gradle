/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.java.compile.incremental

import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import spock.lang.Unroll

abstract class AbstractCrossTaskIncrementalGroovyCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.GROOVY

    @ToBeFixedForConfigurationCache(
        bottomSpecs = [
            "CrossTaskIncrementalGroovyCompilationUsingClassDirectoryIntegrationTest",
            "CrossTaskIncrementalGroovyCompilationUsingJarIntegrationTest"
        ],
        because = "gradle/configuration-cache#270"
    )
    // This is a constant test that is not incremental for Groovy
    def "recompiles dependent class in case a constant is computed from another constant"() {
        source api: ["class A { public static final int FOO = 10; }"], impl: ['class B { public static final int BAR = 2 + A.FOO; }', 'class C { }']
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ['class A { public static final int FOO = 100; }']
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses 'B', 'C'
    }

    @Unroll
    // This is a constant test that is not incremental for Groovy
    def "recompiles outermost class when #visibility inner class contains constant reference"() {
        source api: [
            "class A { public static final int EVIL = 666; }",
        ], impl: [
            "class B {}",
            "class C { $visibility static class Inner { int foo() { return A.EVIL; } } }",
            "class D { $visibility class Inner { int foo() { return A.EVIL; } } }",
            "class E { void foo() { Runnable r = new Runnable() { public void run() { int x = A.EVIL; } }; } }",
            """class F {
                    int foo() { return A.EVIL; }
                    $visibility static class Inner { }
                }""",
        ]

        impl.snapshot { run("impl:${language.compileTaskName}") }

        when:
        source api: ["class A { public static final int EVIL = 0; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run("impl:${language.compileTaskName}")

        then:
        impl.recompiledClasses('B', 'C', 'C$Inner', 'D', 'D$Inner', 'E', 'E$1', 'F', 'F$Inner')

        where:
        visibility << ['public', 'private', '']
    }

}
