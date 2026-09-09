/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.classpath.transforms;

import org.gradle.api.internal.provider.PropertyCallSites;
import org.gradle.api.internal.provider.ProviderCallSites;
import org.gradle.internal.instrumentation.api.jvmbytecode.BridgeMethodBuilder;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** Adds source metadata to statically dispatched Java and Kotlin property mutations. */
final class PropertyCallSiteInterceptor implements JvmBytecodeCallInterceptor {
    private static final List<Target> TARGETS = targets();
    private final InstrumentationMetadata metadata;
    private final @Nullable String sourceRoot;

    PropertyCallSiteInterceptor(InstrumentationMetadata metadata) {
        this(metadata, null);
    }

    PropertyCallSiteInterceptor(InstrumentationMetadata metadata, @Nullable String sourceRoot) {
        this.metadata = metadata;
        this.sourceRoot = sourceRoot;
    }

    private static List<Target> targets() {
        List<Target> targets = new ArrayList<>();
        for (Class<?> helper : new Class<?>[] {PropertyCallSites.class, ProviderCallSites.class}) {
            for (Method method : helper.getDeclaredMethods()) {
                if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) && !method.getName().equals("withLocation")) {
                    targets.add(new Target(method));
                }
            }
        }
        return targets;
    }

    @Override
    public boolean visitMethodInsn(MethodVisitorScope visitor, String className, int opcode, String owner, String name, String descriptor,
                                   boolean isInterface, Supplier<MethodNode> readMethodNode) {
        boolean assignment = opcode == Opcodes.INVOKESTATIC && owner.equals("org/gradle/kotlin/dsl/PropertyExtensionsKt") && name.equals("assign");
        if ((!assignment && opcode != Opcodes.INVOKEINTERFACE && opcode != Opcodes.INVOKEVIRTUAL) || !(visitor instanceof CallSiteSource)) {
            return false;
        }
        CallSiteSource source = (CallSiteSource) visitor;
        String file = source.getSourceFileName();
        int line = source.getLineNumber();
        if (file == null || line <= 0 || !(file.endsWith(".java") || file.endsWith(".kt") || file.endsWith(".kts"))) {
            return false;
        }
        Target selected = null;
        for (Target target : TARGETS) {
            if (assignment && target.name.equals("set") && target.assignmentDescriptor.equals(descriptor)) {
                captureLocation(visitor, className, file, line, target.argumentCount);
                visitor._INVOKESTATIC("org/gradle/kotlin/dsl/internal/PropertyAssignmentCallSites", "assign", target.helperDescriptor);
                return true;
            }
            if (!assignment && target.name.equals(name) && target.descriptor.equals(descriptor)
                && target.matches(owner, metadata)) {
                if (target.receiver.equals(owner)) {
                    selected = target;
                    break;
                }
                if (selected == null) {
                    selected = target;
                }
            }
        }
        if (selected != null) {
            captureLocation(visitor, className, file, line, selected.argumentCount);
            visitor._INVOKESTATIC(selected.helperType, name, selected.helperDescriptor);
            return true;
        }
        return false;
    }

    private void captureLocation(MethodVisitorScope visitor, String className, String file, int line, int argumentCount) {
        // All supported API arguments occupy one reference slot. Copy the receiver without
        // changing evaluation order or introducing locals into the original method.
        switch (argumentCount) {
            case 0:
                visitor.visitInsn(Opcodes.DUP);
                break;
            case 1:
                visitor.visitInsn(Opcodes.DUP2);
                visitor.visitInsn(Opcodes.POP);
                break;
            case 3:
                // ProviderFactory.zip: the left input, rather than the factory, owns the context.
            case 2:
                visitor.visitInsn(Opcodes.DUP2_X1);
                visitor.visitInsn(Opcodes.POP2);
                visitor.visitInsn(Opcodes.DUP_X2);
                break;
            default:
                throw new IllegalArgumentException("Unsupported property call arity");
        }
        visitor._LDC(file);
        visitor._LDC(line);
        if (sourceRoot != null && (file.endsWith(".java") || file.endsWith(".kt")) && file.indexOf('/') < 0 && file.indexOf('\\') < 0) {
            String language = file.endsWith(".java") ? "java" : "kotlin";
            String packagePath = className.substring(0, className.lastIndexOf('/') + 1);
            visitor._LDC(sourceRoot + "/src/main/" + language + "/" + packagePath + file);
            visitor._INVOKESTATIC("org/gradle/api/internal/provider/PropertySourceLocations", "capture",
                "(Ljava/lang/Object;Ljava/lang/String;ILjava/lang/String;)Lorg/gradle/api/internal/provenance/SourceLocation;");
        } else {
            visitor._INVOKESTATIC("org/gradle/api/internal/provider/PropertySourceLocations", "capture",
                "(Ljava/lang/Object;Ljava/lang/String;I)Lorg/gradle/api/internal/provenance/SourceLocation;");
        }
    }

    @Override
    public @Nullable BridgeMethodBuilder findBridgeMethodBuilder(String className, int tag, String owner, String name, String descriptor) {
        // A method reference has no mutation call-site line at invocation time.
        return null;
    }

    @Override
    public BytecodeInterceptorType getType() {
        return BytecodeInterceptorType.INSTRUMENTATION;
    }

    private static final class Target {
        private final int argumentCount;
        private final Type helperType;
        private final String name;
        private final String receiver;
        private final Class<?> receiverClass;
        private final String descriptor;
        private final String helperDescriptor;
        private final String assignmentDescriptor;

        private boolean matches(String owner, InstrumentationMetadata metadata) {
            if (receiver.equals(owner) || metadata.isInstanceOf(owner, receiver)) {
                return true;
            }
            // Project artifact transforms may have no hierarchy index. Resolve only Gradle's
            // own API types, never user classes or their class loaders.
            if (owner.startsWith("org/gradle/api/provider/") || owner.startsWith("org/gradle/api/internal/provider/")) {
                try {
                    return receiverClass.isAssignableFrom(Class.forName(owner.replace('/', '.'), false, PropertyCallSites.class.getClassLoader()));
                } catch (ClassNotFoundException unavailable) {
                    return false;
                }
            }
            return false;
        }

        private Target(Method method) {
            receiverClass = method.getParameterTypes()[0];
            helperType = Type.getType(method.getDeclaringClass());
            name = method.getName();
            helperDescriptor = Type.getMethodDescriptor(method);
            Type[] arguments = Type.getArgumentTypes(helperDescriptor);
            receiver = arguments[0].getInternalName();
            argumentCount = arguments.length - 2;
            assignmentDescriptor = Type.getMethodDescriptor(Type.getReturnType(helperDescriptor), Arrays.copyOfRange(arguments, 0, arguments.length - 1));
            descriptor = Type.getMethodDescriptor(Type.getReturnType(helperDescriptor), Arrays.copyOfRange(arguments, 1, arguments.length - 1));
        }
    }
}
