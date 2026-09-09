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

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps Kotlin's generated line numbers back to the class's source file. */
final class CallSiteSourceMap {
    private static final Pattern LINE = Pattern.compile("(\\d+)(?:#(\\d+))?(?:,(\\d+))?:(\\d+)(?:,(\\d+))?");
    private final List<int[]> ranges = new ArrayList<>();
    private final boolean mapped;

    CallSiteSourceMap(@Nullable String source, @Nullable String debug) {
        mapped = debug != null;
        if (debug == null || source == null) {
            return;
        }
        try {
            Map<Integer, String> files = new HashMap<>();
            String[] lines = debug.split("\\r?\\n");
            String section = "";
            boolean kotlin = false;
            int fileId = 1;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.startsWith("*S ")) {
                    kotlin = line.equals("*S Kotlin");
                } else if (line.startsWith("*")) {
                    section = line;
                } else if (kotlin && section.equals("*F")) {
                    boolean path = line.startsWith("+ ");
                    String entry = path ? line.substring(2) : line;
                    int separator = entry.indexOf(' ');
                    files.put(Integer.parseInt(entry.substring(0, separator)), entry.substring(separator + 1));
                    if (path) {
                        i++;
                    }
                } else if (kotlin && section.equals("*L")) {
                    Matcher match = LINE.matcher(line);
                    if (!match.matches()) {
                        throw new IllegalArgumentException("Invalid source map line");
                    }
                    int input = Integer.parseInt(match.group(1));
                    fileId = match.group(2) == null ? fileId : Integer.parseInt(match.group(2));
                    int count = match.group(3) == null ? 1 : Integer.parseInt(match.group(3));
                    int output = Integer.parseInt(match.group(4));
                    int increment = match.group(5) == null ? 1 : Integer.parseInt(match.group(5));
                    if (input <= 0 || count <= 0 || output <= 0 || increment <= 0) {
                        throw new IllegalArgumentException("Invalid source map range");
                    }
                    ranges.add(new int[] {input, count, output, increment, fileId == 1 && source.equals(files.get(fileId)) ? 1 : 0});
                }
            }
        } catch (IllegalArgumentException | IndexOutOfBoundsException invalid) {
            ranges.clear();
        }
    }

    int originalLine(int line) {
        if (!mapped) {
            return line;
        }
        int result = -1;
        for (int[] range : ranges) {
            long offset = (long) line - range[2];
            if (offset >= 0 && offset < (long) range[1] * range[3]) {
                if (result != -1 || range[4] == 0) {
                    return -1;
                }
                long original = range[0] + offset / range[3];
                result = original <= Integer.MAX_VALUE ? (int) original : -1;
            }
        }
        return result;
    }
}
