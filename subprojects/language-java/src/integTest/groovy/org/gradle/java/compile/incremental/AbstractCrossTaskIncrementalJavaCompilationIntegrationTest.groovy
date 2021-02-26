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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Unroll

abstract class AbstractCrossTaskIncrementalJavaCompilationIntegrationTest extends AbstractCrossTaskIncrementalCompilationIntegrationTest {
    CompiledLanguage language = CompiledLanguage.JAVA

    @Unroll
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "change in an upstream class with non-private constant does not rebuild if same constant is used (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class ImplA extends A { $constantType foo() { return $constantValue; }}", "class ImplB {int foo() { return 2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses()

        where:
        constantType | constantValue
        'boolean'    | 'false'
        'int'        | '55542'
        'String'     | '"foo"'
        'String'     | '"foo" + "bar"'
    }

    @Unroll
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "constant value change in an upstream class causes rebuild if referenced at constant declaration (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X { final static $constantType v = B.x;  }", "class Y {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; /* change value */ ; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

        where:
        constantType | constantValue   | newValue
        'boolean'    | 'false'         | 'true'
        'int'        | '55542'         | '444'
        'String'     | '"foo"'         | '"bar"'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    @Unroll
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "constant value change in an upstream class causes rebuild if referenced in method body (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X { $constantType method() { return B.x; }  }", "class Y {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; /* change value */ ; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

        where:
        constantType | constantValue   | newValue
        'boolean'    | 'false'         | 'true'
        'int'        | '55542'         | '444'
        'String'     | '"foo"'         | '"bar"'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    @Unroll
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "constant value change in an upstream class cause rebuild if inherited (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X extends B { }", "class Y { }", "class Z { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; /* change value */ ; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

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
    @Requires(TestPrecondition.JDK9_OR_LATER)
    // We cannot track where is constant located
    def "class change in an upstream class cause rebuild if constant is referenced (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X { $constantType method() { return B.x; } }", "class Y { }", "class Z { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $constantValue; void blah() {  } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X')

        where:
        constantType | constantValue
        'boolean'    | 'false'
        'int'        | '55542'
        'String'     | '"foo"'
        'String'     | '"foo" + "bar"'
    }

    @Unroll
    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "constant value change in an upstream class causes rebuild if inherited constant value is referenced in another class (#constantType)"() {
        source api: ["class A {}", "class B { final static $constantType x = $constantValue; }"], impl: ["class X extends B { }", "class Y {$constantType foo() { return X.x; }}", "class Z { }"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static $constantType x = $newValue; /* change value */ ; void blah() { /* avoid flakiness by changing compiled file length*/ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y')

        where:
        constantType | constantValue   | newValue
        'boolean'    | 'false'         | 'true'
        'int'        | '55542'         | '444'
        'String'     | '"foo"'         | '"bar"'
        'String'     | '"foo" + "bar"' | '"bar"'
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "deletion of jar with non-private constant causes compilation failure if constant is used"() {
        source api: ["class A { public final static int x = 1; }"], impl: ["class X { int x() { return A.x;} }", "class Y {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        clearImplProjectDependencies()
        fails "impl:${language.compileTaskName}"

        then:
        impl.noneRecompiled()
    }

    @Requires(TestPrecondition.JDK7_OR_EARLIER)
    def "changing an unused non-private constant causes full rebuild"() {
        println(buildFile.text)
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplA', 'ImplB')
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    //  Can re-enable with compiler plugins. See gradle/gradle#1474
    def "changing an unused non-private constant doesn't cause full rebuild"() {
        source api: ["class A {}", "class B { final static int x = 1; }"], impl: ["class ImplA extends A {}", "class ImplB extends B {}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { /* change */ }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('ImplB')
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

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "recompiles in case of conflicting changing constant values"() {
        source api: ["class A { final static int x = 3; }", "class B { final static int x = 3; final static int y = -2; }"],
            impl: ["class X { int foo() { return 3; }}", "class Y {int foo() { return -2; }}"]
        impl.snapshot { run language.compileTaskName }

        when:
        source api: ["class B { final static int x = 3 ; final static int y = -3;  void blah() { /*  change irrelevant to constant value x */ } }"]
        source api: ["class A { final static int x = 2 ; final static int y = -2;  void blah() { /*  change irrelevant to constant value y */ } }"]
        run "impl:${language.compileTaskName}"

        then:
        impl.recompiledClasses('X', 'Y')
    }

    @Requires(TestPrecondition.JDK9_OR_LATER)
    def "does not recompile in case of conflicting changing constant values"() {
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
}
