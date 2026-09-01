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

package org.gradle.api.internal.provider

import spock.lang.Specification

class CollaborativePropertyTest extends Specification {
    private static final List<String> CONTRIBUTOR_ORDER = ["base-plugin", "feature-plugin", "override-plugin", "build-author"]

    def "composes attributed updates immediately and retains them when the source changes"() {
        given:
        def property = collaborativeStringProperty()
        withContributor("base-plugin") {
            property.convention("default")
        }

        when:
        withContributor("feature-plugin") {
            property.set(property.map { it + "-feature" })
        }

        then:
        property.get() == "default-feature"

        when:
        withSource {
            property.set("user")
        }

        then:
        property.get() == "user-feature"

        when:
        withContributor("override-plugin") {
            property.set(property.zip(Providers.of("override")) { current, suffix -> current + "-" + suffix })
        }

        then:
        property.get() == "user-feature-override"

        when:
        withSource {
            property.set("replacement")
        }

        then:
        property.get() == "replacement-feature-override"
    }

    def "source selection remains explicit over convention before applying updates"() {
        given:
        def property = collaborativeStringProperty()
        withContributor("base-plugin") {
            property.convention("first-default")
        }
        withContributor("feature-plugin") {
            property.set(property.map { it.toUpperCase() })
        }

        when:
        withSource {
            property.set("user")
        }
        withContributor("base-plugin") {
            property.convention("second-default")
        }

        then:
        property.get() == "USER"

        when:
        withSource {
            property.unset()
        }

        then:
        property.get() == "SECOND-DEFAULT"
    }

    def "queries observe the valid update prefix accepted at that point"() {
        given:
        def property = collaborativeStringProperty()
        withSource {
            property.set("source")
        }

        when:
        withContributor("feature-plugin") {
            property.set(property.map { it + "-feature" })
        }

        then:
        property.present
        property.getOrNull() == "source-feature"

        when:
        withContributor("override-plugin") {
            property.set(property.flatMap { Providers.of(it + "-override") })
        }

        then:
        property.get() == "source-feature-override"
    }

    def "a shallow copy snapshots both the collaborative source and update prefix"() {
        given:
        def property = collaborativeStringProperty()
        withSource {
            property.set("first")
        }
        withContributor("feature-plugin") {
            property.set(property.map { it + "-feature" })
        }
        def copy = property.shallowCopy()

        when:
        withSource {
            property.set("second")
        }
        withContributor("override-plugin") {
            property.set(property.map { it + "-override" })
        }

        then:
        copy.get() == "first-feature"
        property.get() == "second-feature-override"
    }

    def "invalid update order fails before transformations are evaluated"() {
        given:
        def evaluated = false
        def property = collaborativeStringProperty()
        withSource {
            property.set("source")
        }
        withContributor("override-plugin", "override.gradle:12") {
            property.set(property.map {
                evaluated = true
                it + "-override"
            })
        }
        withContributor("feature-plugin", "feature.gradle:8") {
            property.set(property.map {
                evaluated = true
                it + "-feature"
            })
        }

        when:
        property.get()

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains("contributor updates are out of order")
        failure.message.contains("base-plugin < feature-plugin < override-plugin < build-author")
        failure.message.contains("override-plugin -> feature-plugin")
        failure.message.contains("override-plugin: Map at override.gradle:12")
        !evaluated
    }

    def "a local constraint can validate an already composed reversed trace"() {
        given:
        def property = collaborativeStringProperty()
        withSource {
            property.set("source")
        }
        withContributor("override-plugin") {
            property.set(property.map { it + "-override" })
        }
        withContributor("feature-plugin") {
            property.set(property.map { it + "-feature" })
        }

        when:
        property.get()

        then:
        thrown(IllegalStateException)

        when:
        property.addCollaborationConstraint("override-plugin", "feature-plugin")

        then:
        property.get() == "source-override-feature"
    }

    def "cyclic local constraints fail on observation"() {
        given:
        def property = collaborativeStringProperty()
        property.addCollaborationConstraint("feature-plugin", "override-plugin")
        property.addCollaborationConstraint("override-plugin", "feature-plugin")

        when:
        property.isPresent()

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains("local contributor ordering constraints contain a cycle")
    }

    def "rejects unattributed mutation unauthorized convention and contributor replacement"() {
        given:
        def property = collaborativeStringProperty()

        when:
        property.set("unattributed")

        then:
        def unattributed = thrown(IllegalStateException)
        unattributed.message.contains("no declarative source or contributor context is active")

        when:
        withContributor("feature-plugin") {
            property.convention("not-owned")
        }

        then:
        def convention = thrown(IllegalStateException)
        convention.message.contains("only owning contributor 'base-plugin'")

        when:
        withContributor("feature-plugin") {
            property.set(Providers.of("replacement"))
        }

        then:
        def replacement = thrown(IllegalStateException)
        replacement.message.contains("not a supported structural self-update")
    }

    def "lifecycle closure validates order and disallowChanges leaves the composed plan lazy"() {
        given:
        def property = collaborativeStringProperty()
        withSource {
            property.set("source")
        }
        withContributor("override-plugin") {
            property.set(property.map { it + "-override" })
        }
        withContributor("feature-plugin") {
            property.set(property.map { it + "-feature" })
        }

        when:
        property.disallowChanges()

        then:
        thrown(IllegalStateException)

        when:
        property.addCollaborationConstraint("override-plugin", "feature-plugin")
        property.disallowChanges()

        then:
        property.get() == "source-override-feature"

        when:
        property.addCollaborationConstraint("feature-plugin", "build-author")

        then:
        thrown(IllegalStateException)
    }

    def "finalizeValue fixes the currently composed plan"() {
        given:
        def source = new DefaultProperty<String>(PropertyHost.NO_OP, String).value("first")
        def property = collaborativeStringProperty()
        withSource {
            property.set(source)
        }
        withContributor("feature-plugin") {
            property.set(property.map { it + "-feature" })
        }

        when:
        property.finalizeValue()
        source.set("second")

        then:
        property.get() == "first-feature"

        when:
        withSource {
            property.set("replacement")
        }

        then:
        thrown(IllegalStateException)
    }

    def "finalizeValueOnRead validates before scheduling finalization"() {
        given:
        def property = collaborativeStringProperty()
        withContributor("override-plugin") {
            property.set(property.map { it + "-override" })
        }
        withContributor("feature-plugin") {
            property.set(property.map { it + "-feature" })
        }

        when:
        property.finalizeValueOnRead()

        then:
        thrown(IllegalStateException)
    }

    def "collection and map properties use the same collaborative source pipeline"() {
        given:
        def list = new DefaultListProperty<String>(PropertyHost.NO_OP, String)
        list.enableCollaboration("base-plugin", CONTRIBUTOR_ORDER)
        def map = new DefaultMapProperty<String, String>(PropertyHost.NO_OP, String, String)
        map.enableCollaboration("base-plugin", CONTRIBUTOR_ORDER)

        withContributor("base-plugin") {
            list.convention(["default"])
            map.convention([default: "value"])
        }
        withContributor("feature-plugin") {
            list.set(list.map { it + "feature" })
            map.set(map.map { it + [feature: "value"] })
        }

        when:
        withSource {
            list.set(["user"])
            map.set([user: "value"])
        }

        then:
        list.get() == ["user", "feature"]
        map.get() == [user: "value", feature: "value"]
    }

    private static DefaultProperty<String> collaborativeStringProperty() {
        def property = new DefaultProperty<String>(PropertyHost.NO_OP, String)
        property.enableCollaboration("base-plugin", CONTRIBUTOR_ORDER)
        return property
    }

    private static void withSource(Closure<?> action) {
        CollaborativePropertyContext.withSource(action as Runnable)
    }

    private static void withContributor(String contributor, Closure<?> action) {
        CollaborativePropertyContext.withContributor(contributor, action as Runnable)
    }

    private static void withContributor(String contributor, String origin, Closure<?> action) {
        CollaborativePropertyContext.withContributor(contributor, origin, action as Runnable)
    }
}
