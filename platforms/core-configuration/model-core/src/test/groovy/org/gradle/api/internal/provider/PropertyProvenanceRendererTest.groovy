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

import org.gradle.api.internal.provenance.Attribution
import org.gradle.api.internal.provenance.ContributorKey
import org.gradle.api.internal.provenance.DiagnosticOrigin
import org.gradle.api.internal.provenance.FailedOperation
import org.gradle.api.internal.provenance.OrdinaryProvenanceState
import org.gradle.api.internal.provenance.ProvenanceCheckpoint
import org.gradle.api.internal.provenance.ScopeIdentity
import org.gradle.api.internal.provenance.SemanticOperation
import org.gradle.api.internal.provenance.SourceLocation
import spock.lang.Specification
import spock.lang.TempDir

class PropertyProvenanceRendererTest extends Specification {
    @TempDir
    File directory

    def owner = new ScopeIdentity(':', ':')

    def 'known #kind script locations use native path and line after checkpoint transport'() {
        given:
        def script = new File(directory, 'script with spaces.gradle.kts')
        script.text = '// script'
        def attribution = author(kind, script.toURI().toString(), script.name)
        def original = view(attribution)
        def checkpoint = new ProvenanceCheckpoint(original, null).encode()
        def restored = ProvenanceCheckpoint.decode(checkpoint).view

        when:
        def configuration = PropertyProvenanceRenderer.configuration(restored)
        def failure = PropertyProvenanceRenderer.failure(restored, new FailedOperation('set', attribution))

        then:
        configuration == """Configuration of task ':check' property 'value':
    set by ${label} (${script.absolutePath}:12)"""
        failure.contains("failed set by ${label} (${script.absolutePath}:12)")
        !configuration.contains('\u001b')
        new ProvenanceCheckpoint(restored, null).encode() == checkpoint

        when:
        script.delete()

        then:
        PropertyProvenanceRenderer.configuration(restored).contains("(${script.name}:12)")
        !PropertyProvenanceRenderer.configuration(restored).contains(script.absolutePath)

        where:
        kind                                   | label
        DiagnosticOrigin.Kind.PROJECT_SCRIPT   | 'build script'
        DiagnosticOrigin.Kind.APPLIED_SCRIPT   | 'applied script'
        DiagnosticOrigin.Kind.SETTINGS_SCRIPT  | 'settings script'
        DiagnosticOrigin.Kind.INIT_SCRIPT      | 'init script'
    }

    def 'unrelated helper filenames do not link to the active script'() {
        given:
        def script = new File(directory, 'build.gradle.kts')
        script.text = '// script'
        def attribution = author(DiagnosticOrigin.Kind.PROJECT_SCRIPT, script.toURI().toString(), 'Helper.kt')

        expect:
        PropertyProvenanceRenderer.configuration(view(attribution)).endsWith('set by build script (Helper.kt:12)')
    }

    def 'unresolvable and non-script origins use filename and line'() {
        expect:
        PropertyProvenanceRenderer.configuration(view(author(kind, identifier, 'Plugin.kt'))).contains('(Plugin.kt:12)')

        where:
        kind                                 | identifier
        DiagnosticOrigin.Kind.PROJECT_SCRIPT | 'https://example.org/Plugin.kt'
        DiagnosticOrigin.Kind.PROJECT_SCRIPT | 'not a URI'
        DiagnosticOrigin.Kind.PROJECT_SCRIPT | 'file://remote/Plugin.kt'
        DiagnosticOrigin.Kind.PLUGIN_CLASS   | 'com.example.Plugin'
        DiagnosticOrigin.Kind.UNKNOWN        | ''
    }

    def 'transported local plugin paths are navigable only while the source exists'() {
        given:
        def file = new File(directory, 'Plugin.java')
        file.text = '// source'
        def attribution = author(DiagnosticOrigin.Kind.PLUGIN_CLASS, 'example.Plugin', 'Plugin.java')
            .withLocation(new SourceLocation('Plugin.java', 12, file.absolutePath))
        def restored = ProvenanceCheckpoint.decode(new ProvenanceCheckpoint(view(attribution), null).encode()).view

        expect:
        PropertyProvenanceRenderer.configuration(restored).contains("(${file.absolutePath}:12)")

        when:
        file.delete()

        then:
        PropertyProvenanceRenderer.configuration(restored).endsWith('(Plugin.java:12)')
    }

    private Attribution author(DiagnosticOrigin.Kind kind, String identifier, String file) {
        new Attribution(new ContributorKey('domain', ContributorKey.Kind.UNKNOWN, ''),
            new DiagnosticOrigin(kind, identifier, 'script'), owner, 'application').withLocation(new SourceLocation(file, 12))
    }

    private def view(Attribution attribution) {
        def state = new OrdinaryProvenanceState(owner, 'property')
        state.acceptedBinding(attribution, SemanticOperation.EXPLICIT_BINDING)
        state.getEffectiveProvenance("task ':check' property 'value'")
    }
}
