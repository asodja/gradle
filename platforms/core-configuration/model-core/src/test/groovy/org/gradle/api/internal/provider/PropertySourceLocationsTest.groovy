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

package org.gradle.api.internal.provider

import org.gradle.api.internal.provenance.SourceLocation
import spock.lang.Specification

class PropertySourceLocationsTest extends Specification {
    def 'ordinary receivers do not allocate or validate diagnostic metadata'() {
        expect:
        PropertySourceLocations.capture(new Object(), null, -1) == null
        PropertySourceLocations.capture(new Object(), null, -1, '/missing/source') == null
    }

    def 'diagnostic receivers retain only supplied source descriptors'() {
        given:
        def receiver = Stub(ProvenanceAware)

        expect:
        PropertySourceLocations.capture(receiver, 'Plugin.java', 9) == new SourceLocation('Plugin.java', 9)
        PropertySourceLocations.capture(receiver, 'Plugin.java', 9, '/work/src/main/java/Plugin.java') ==
            new SourceLocation('Plugin.java', 9, '/work/src/main/java/Plugin.java')
    }
}
