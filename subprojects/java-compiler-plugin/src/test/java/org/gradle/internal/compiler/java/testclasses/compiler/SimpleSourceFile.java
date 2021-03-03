package org.gradle.internal.compiler.java.testclasses.compiler;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;

class SimpleSourceFile extends SimpleJavaFileObject {

    private final String content;

    public SimpleSourceFile(String qualifiedClassName, String testSource) {
        super(URI.create(String.format(
                "file://%s%s", qualifiedClassName.replaceAll("\\.", "/"),
                Kind.SOURCE.extension)), Kind.SOURCE);
        content = testSource;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }

}

