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

import groovy.test.NotYetImplemented
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

abstract class AbstractCrossTaskIncrementalJavaCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Unroll
    def "change in an upstream class with non-private constant causes rebuild correctly incrementally"() {
        source api: ["class A { final static int x = 1; }", "class B { final static int y = 1; }"], impl: ["class X { int foo() { return A.x; }}", "class Y {int foo() { return B.y; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        // A is changed so we expect X to recompile
        source api: ["class A { final static int x = 2; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

        when:
        // B is changed so we expect Y to recompile
        long lastClassChangeTime = System.currentTimeMillis()
        source api: ["class B { final static int y = 2; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClassesSince(lastClassChangeTime, 'Y')

        when:
        // A and B are changed so we expect X, Y to recompile
        lastClassChangeTime = System.currentTimeMillis()
        source api: ["class A { final static int x = 3; }",
                     "class B { final static int y = 3; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClassesSince(lastClassChangeTime, 'X', 'Y')

        when:
        // No change, so we expect no recompilation
        lastClassChangeTime = System.currentTimeMillis()
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiledSince(lastClassChangeTime)
    }

    @Unroll
    def "change in an upstream class with non-private constant causes rebuild if constant is referenced in method body (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class ImplA { $constantType foo() { return B.x; }}", "class ImplB {int foo() { return 2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplA')

        where:
        constantType | constantValue   | newValue
        'boolean'    | 'false'         | 'true'
        'byte'       | '(byte) 125'    | '(byte) 126'
        'short'      | '(short) 666'   | '(short) 555'
        'int'        | '55542'         | '444'
        'long'       | '5L'            | '689L'
        'float'      | '6f'            | '6.5f'
        'double'     | '7d'            | '7.2d'
        'String'     | '"foo"'         | '"bar"'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    @Unroll
    def "change in an upstream class with non-private constant causes rebuild if constant is referenced in field (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class ImplA extends A { final $constantType foo = B.x; }", "class ImplB {int foo() { return 2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplA')

        where:
        constantType | constantValue   | newValue
        'int'        | '55542'         | '444'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    def "non-abi change to constant origin class does not causes compilation"() {
        source impl: ["class A { final static int x = 1; int method() { return 1; } }", "class B { int method() { return A.x; }  }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source impl: ["class A { final static int x = 1; int method() { return 2; } }"]
        run language.compileTaskName

        then:
        impl.recompiledClasses('A', 'B')
    }

    def "non-abi change to constant origin class does not causes compilation but constant change does incrementally"() {
        source api: ["class A {}", "class B { final static int x = 1; int method() { return 1; } }"], impl: ["class X { int method() { return B.x; } }", "class Y {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        // non-abi-change
        source api: ["class B { final static int x = 1; int method() { return 2; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()

        when:
        // Constant change
        long lastClassChangeTime = System.currentTimeMillis()
        source api: ["class B { final static int x = 2; int method() { return 2; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClassesSince(lastClassChangeTime, 'X')

        when:
        // non-abi change
        lastClassChangeTime = System.currentTimeMillis()
        source api: ["class B { final static int x = 2; int method() { return 3; } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiledSince(lastClassChangeTime)
    }

    def "changing upstream constant causes compilation for downstream constants"() {
        source api: ["class A { final static int x = 1; }", "class B { final static int x = A.x; }"],
            impl: ["class X { final static int x = B.x; }",
                   "class Y { final static int x = X.x; }",
                   "class Z { final static int x = Y.x; }",
                   "class W {  }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { final static int x = 2; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y', 'Z')
    }

    def "changing upstream constant causes compilation for downstream constants for binary expression"() {
        source api: ["class A { final static int x = 1; }", "class B { final static int x = A.x + 1; }"],
            impl: ["class X { final static int x = B.x + 1; }",
                   "class Y { final static int x = X.x + 1; }",
                   "class Z { final static int x = Y.x + 1; }",
                   "class W {  }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { final static int x = 2; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y', 'Z')
    }

    def "changing an unused non-private constant recompile child classes"() {
        println(buildFile.text)
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 2; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ }  }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
    }

    def "removing an unused non-private constant recompile child classes"() {
        println(buildFile.text)
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
    }

    def "changing an unused non-private constant doesn't cause any compilation"() {
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA {}", "class ImplB {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 2; /* change */ void blah() { /* avoid flakiness by changing compiled file length*/ }  }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    def "removing an unused non-private constant doesn't cause any compilation"() {
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA {}", "class ImplB {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    // This behavior is kept for backward compatibility - may be removed in the future
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "recompiles when upstream module-info changes with manual module path"() {
        file("api/src/main/${language.name}/a/A.${language.name}").text = "package a; public class A {}"
        file("impl/src/main/${language.name}/b/B.${language.name}").text = "package b; import a.A; class B extends A {}"
        def moduleInfo = file("api/src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module api {
                exports a;
            }
        """
        file("impl/src/main/${language.name}/module-info.${language.name}").text = """
            module impl {
                requires api;
            }
        """
        file("impl/build.gradle") << """
            def layout = project.layout
            compileJava.doFirst {
                options.compilerArgs << "--module-path" << classpath.join(File.pathSeparator)
                classpath = layout.files()
            }
        """
        succeeds "impl:${language.compileTaskName}"

        when:
        moduleInfo.text = """
            module api {
            }
        """

        then:
        fails "impl:${language.compileTaskName}"
        result.hasErrorOutput("package a is not visible")
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "recompiles when upstream module-info changes"() {
        file("api/src/main/${language.name}/a/A.${language.name}").text = "package a; public class A {}"
        file("impl/src/main/${language.name}/b/B.${language.name}").text = "package b; import a.A; class B extends A {}"
        def moduleInfo = file("api/src/main/${language.name}/module-info.${language.name}")
        moduleInfo.text = """
            module api {
                exports a;
            }
        """
        file("impl/src/main/${language.name}/module-info.${language.name}").text = """
            module impl {
                requires api;
            }
        """
        succeeds "impl:${language.compileTaskName}"

        when:
        moduleInfo.text = """
            module api {
            }
        """

        then:
        fails "impl:${language.compileTaskName}"
        result.hasErrorOutput("package a is not visible")
    }

    def "does not recompiles in case of conflicting changing constant values"() {
        source api: ["class A { final static int x = 3; }", "class B { final static int x = 3; final static int y = -2; }"],
            impl: ["class X { int foo() { return 3; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 3 ; final static int y = -3;  void blah() { /*  change irrelevant to constant value x */ } }"]
        source api: ["class A { final static int x = 2 ; final static int y = -2;  void blah() { /*  change irrelevant to constant value y */ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses()
    }

    @NotYetImplemented
    // This would be possible since constant origins on Annotations are not put to Constants pool even on OpenJDK,
    // but it's not yet implemented since we would have to track every constants as (symbol, value) pair
    def "ignores irrelevant changes to constant values in annotations"() {
        source api: ["class A { final static int x = 1; final static int y = -1; }",
                     """import java.lang.annotation.Retention;
               import java.lang.annotation.RetentionPolicy;
               @Retention(RetentionPolicy.RUNTIME)
               @interface B { int value(); }"""
        ], impl: [
            // cases where it's relevant, ABI-wise
            "@B(A.x) class OnClass {}",
            "class OnMethod { @B(A.y) void foo() {} }",
            "class OnField { @B(A.x) String foo; }",
            "class OnParameter { void foo(@B(A.y) int x) {} }",
            "class InMethodBody { void foo(int x) { @B(A.x) int value = 5; } }"
        ]

        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class A { final static int x = $newXValue; final static int y = $newYValue; }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses(compiledClasses.split(','))

        where:
        newXValue | newYValue | compiledClasses
        '2'       | '-1'      | 'OnClass,OnField,InMethodBody'
        '1'       | '-2'      | 'OnMethod,OnParameter'
    }

}
