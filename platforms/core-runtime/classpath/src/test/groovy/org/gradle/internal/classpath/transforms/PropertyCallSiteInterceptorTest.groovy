/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.internal.classpath.transforms

import org.gradle.api.internal.provider.PropertyCallSites
import org.gradle.api.internal.provider.ProviderCallSites
import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata
import org.gradle.model.internal.asm.MethodVisitorScope
import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import spock.lang.Specification

import java.lang.reflect.Modifier

import static org.objectweb.asm.Opcodes.INVOKEINTERFACE
import static org.objectweb.asm.Opcodes.INVOKESPECIAL
import static org.objectweb.asm.Opcodes.INVOKESTATIC
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL

class PropertyCallSiteInterceptorTest extends Specification {
    def 'rewrites every typed mutation helper with the original return descriptor'() {
        given:
        def metadata = Stub(InstrumentationMetadata)
        def interceptor = new PropertyCallSiteInterceptor(metadata)

        expect:
        [PropertyCallSites, ProviderCallSites].collectMany { it.declaredMethods.toList() }.findAll { Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) && it.name != 'withLocation' }.every { method ->
            def helper = Type.getMethodDescriptor(method)
            def args = Type.getArgumentTypes(helper)
            def descriptor = Type.getMethodDescriptor(Type.getReturnType(helper), Arrays.copyOfRange(args, 1, args.length - 1))
            def node = new MethodNode()
            def visitor = new SourceVisitor(node, 'Plugin.java', 19)
            assert interceptor.visitMethodInsn(visitor, 'Plugin', INVOKEINTERFACE, args[0].internalName, method.name, descriptor, true, { null })
            def constants = node.instructions.findAll { it instanceof org.objectweb.asm.tree.LdcInsnNode }*.cst
            assert constants == ['Plugin.java', 19]
            def call = node.instructions.last as MethodInsnNode
            assert call.opcode == INVOKESTATIC
            assert call.owner == Type.getInternalName(method.declaringClass)
            assert call.name == method.name
            assert call.desc == helper
            true
        }
    }

    def 'only intercepts eligible dispatch and source metadata'() {
        given:
        def interceptor = new PropertyCallSiteInterceptor(Stub(InstrumentationMetadata) {
            isInstanceOf('CustomProperty', 'org/gradle/api/provider/Property') >> true
        })
        def node = new MethodNode()
        def visitor = new SourceVisitor(node, file, line)

        expect:
        interceptor.visitMethodInsn(visitor, 'Plugin', opcode, owner, 'set', '(Ljava/lang/Object;)V', false, { null }) == intercepted
        (node.instructions.size() > 0) == intercepted

        where:
        file               | line | opcode          | owner                              | intercepted
        'Plugin.java'      | 4    | INVOKEINTERFACE | 'org/gradle/api/provider/Property'  | true
        'Plugin.kt'        | 4    | INVOKEVIRTUAL   | 'CustomProperty'                   | true
        'build.gradle.kts' | 4    | INVOKEINTERFACE | 'org/gradle/api/provider/Property'  | true
        'Plugin.groovy'    | 4    | INVOKEVIRTUAL   | 'CustomProperty'                   | false
        'build.gradle'    | 4    | INVOKEINTERFACE | 'org/gradle/api/provider/Property'  | false
        'Plugin.java'      | 0    | INVOKEINTERFACE | 'org/gradle/api/provider/Property'  | false
        null               | 4    | INVOKEINTERFACE | 'org/gradle/api/provider/Property'  | false
        'Plugin.java'      | 4    | INVOKESPECIAL   | 'org/gradle/api/provider/Property'  | false
        'Plugin.java'      | 4    | INVOKESTATIC    | 'org/gradle/api/provider/Property'  | false
        'Plugin.java'      | 4    | INVOKEVIRTUAL   | 'Unrelated'                        | false
    }

    def 'recognizes inherited Gradle provider methods without a hierarchy index'() {
        given:
        def interceptor = new PropertyCallSiteInterceptor(Stub(InstrumentationMetadata))
        def node = new MethodNode()

        expect:
        interceptor.visitMethodInsn(new SourceVisitor(node, 'Plugin.java', 9), 'example/Plugin', INVOKEINTERFACE,
            owner, 'map', '(Lorg/gradle/api/Transformer;)Lorg/gradle/api/provider/Provider;', true, { null }) == intercepted

        where:
        owner                                                  | intercepted
        'org/gradle/api/provider/Property'                      | true
        'org/gradle/api/provider/ListProperty'                  | true
        'org/gradle/api/provider/SetProperty'                   | true
        'org/gradle/api/provider/MapProperty'                   | true
        'org/gradle/api/internal/provider/DefaultProperty'      | true
        'org/gradle/api/internal/provider/DiagnosticMapProperty' | true
        'org/gradle/api/provider/ProviderFactory'               | false
        'example/Property'                                     | false
    }

    def 'embeds original plugin source roots only for matching source metadata'() {
        given:
        def interceptor = new PropertyCallSiteInterceptor(Stub(InstrumentationMetadata), '/work/build-logic')
        def node = new MethodNode()

        when:
        interceptor.visitMethodInsn(new SourceVisitor(node, file, 9), 'example/Plugin$Nested', INVOKEINTERFACE,
            'org/gradle/api/provider/Property', 'set', '(Ljava/lang/Object;)V', true, { null })

        then:
        def constants = node.instructions.findAll { it instanceof org.objectweb.asm.tree.LdcInsnNode }*.cst
        constants == [file, 9] + (path == null ? [] : [path])

        where:
        file               | path
        'Plugin.java'      | '/work/build-logic/src/main/java/example/Plugin.java'
        'Plugin.kt'        | '/work/build-logic/src/main/kotlin/example/Plugin.kt'
        'build.gradle.kts' | null
        '../Plugin.java'   | null
    }

    private static class SourceVisitor extends MethodVisitorScope implements CallSiteSource {
        final String sourceFileName
        final int lineNumber

        SourceVisitor(MethodNode node, String file, int line) {
            super(node)
            sourceFileName = file
            lineNumber = line
        }
    }
}
