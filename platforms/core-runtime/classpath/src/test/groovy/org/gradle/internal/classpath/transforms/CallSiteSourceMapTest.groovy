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

import spock.lang.Specification

class CallSiteSourceMapTest extends Specification {
    def 'maps Kotlin lines without attributing inline library bodies to the script'() {
        given:
        def map = new CallSiteSourceMap('build.gradle.kts', """SMAP
build.gradle.kts
Kotlin
*S Kotlin
*F
+ 1 build.gradle.kts
Build_gradle
+ 2 Other.kt
library/OtherKt
*L
1#1,30:1
50#2,4:31
7#1,2:35,2
*S KotlinDebug
*F
+ 1 build.gradle.kts
Build_gradle
*L
12#1:31,4
*E
""")

        expect:
        map.originalLine(generated) == original

        where:
        generated | original
        1         | 1
        30        | 30
        31        | -1
        34        | -1
        35        | 7
        36        | 7
        37        | 8
        38        | 8
        39        | -1
    }

    def 'missing and malformed source metadata have conservative fallbacks'() {
        expect:
        new CallSiteSourceMap('Plugin.java', null).originalLine(12) == 12
        new CallSiteSourceMap('Plugin.kt', 'invalid').originalLine(12) == -1
        new CallSiteSourceMap(null, 'invalid').originalLine(12) == -1
        new CallSiteSourceMap('Plugin.kt', '*S Kotlin\n*F\nbroken').originalLine(12) == -1
    }
}
