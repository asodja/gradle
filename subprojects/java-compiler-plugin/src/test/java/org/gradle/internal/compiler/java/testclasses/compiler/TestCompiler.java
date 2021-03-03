package org.gradle.internal.compiler.java.testclasses.compiler;

import org.gradle.internal.compiler.java.IncrementalCompileTask;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.singletonList;

public class TestCompiler {

    private final Function<File, Optional<String>> relativize;
    private final Consumer<Map<String, Collection<String>>> classNameConsumer;
    private final Consumer<Map<String, Collection<String>>> constantsConsumer;

    public TestCompiler(Function<File, Optional<String>> relativize,
                        Consumer<Map<String, Collection<String>>> classNamesConsumer,
                        Consumer<Map<String, Collection<String>>> constantsConsumer) {
        this.relativize = relativize;
        this.classNameConsumer = classNamesConsumer;
        this.constantsConsumer = constantsConsumer;
    }

    public byte[] compile(String qualifiedClassName, String testSource) {
        StringWriter output = new StringWriter();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        SimpleFileManager fileManager = new SimpleFileManager(compiler.getStandardFileManager(null, null, null));
        List<SimpleSourceFile> compilationUnits = singletonList(new SimpleSourceFile(qualifiedClassName, testSource));
        List<String> arguments = Arrays.asList("-classpath", System.getProperty("java.class.path"));
        JavaCompiler.CompilationTask delegate = compiler.getTask(output, fileManager, null, arguments, null, compilationUnits);
        IncrementalCompileTask task = new IncrementalCompileTask(delegate, relativize, classNameConsumer, constantsConsumer);
        if (task.call()) {
            return fileManager.getCompiled().iterator().next().getCompiledBinaries();
        } else {
            throw new RuntimeException(output.toString());
        }
    }

}
