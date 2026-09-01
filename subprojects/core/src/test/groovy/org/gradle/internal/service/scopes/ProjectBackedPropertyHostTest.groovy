/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service.scopes

import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateInternal
import org.gradle.api.internal.provider.CollaborativePropertyMutation
import org.gradle.api.internal.tasks.TaskExecutionOutcome
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.internal.Describables
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.code.UserCodeApplicationContext
import org.gradle.internal.code.UserCodeSource
import org.gradle.internal.state.ModelObject
import spock.lang.Specification

class ProjectBackedPropertyHostTest extends Specification {
    def state = new ProjectStateInternal()
    def project = Stub(ProjectInternal)
    def host = new ProjectBackedPropertyHost(project)

    def setup() {
        _ * project.displayName >> "<project>"
        _ * project.state >> state
    }

    def "disallows read before completion when property has no producer"() {
        expect:
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.toBeforeEvaluate()
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.toEvaluate()
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.toAfterEvaluate()
        host.beforeRead(null) == "configuration of <project> has not completed yet"
        state.configured()
        host.beforeRead() == null
    }

    def "disallows read before producer task starts when property has producer"() {
        def producer = Stub(ModelObject)
        def task = Stub(TaskInternal)
        def taskState = new TaskStateInternal()
        _ * producer.taskThatOwnsThisObject >> task
        _ * task.state >> taskState
        _ * task.toString() >> "<task>"

        expect:
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.toBeforeEvaluate()
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.toEvaluate()
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.toAfterEvaluate()
        host.beforeRead(producer) == "configuration of <project> has not completed yet"
        state.configured()
        host.beforeRead(producer) == "<task> has not completed yet"

        when:
        taskState.executing = true

        then:
        host.beforeRead(producer) == null

        when:
        taskState.outcome = TaskExecutionOutcome.EXECUTED

        then:
        host.beforeRead(producer) == null
    }

    def "automatic collaborative attribution is disabled by default"() {
        def application = Stub(UserCodeApplicationContext.Application)
        def userCodeContext = Stub(UserCodeApplicationContext) {
            current() >> application
        }
        def host = new ProjectBackedPropertyHost(project, userCodeContext, new DefaultInternalOptions([:]))

        expect:
        host.currentCollaborativeMutation() == null
    }

    def "attributes collaborative mutation to current binary plugin"() {
        def source = new UserCodeSource.Binary(Describables.of("plugin 'com.example.plugin'"), "com.example.Plugin", "com.example.plugin")
        def mutation = currentMutation(source)

        expect:
        !mutation.source
        mutation.contributor == "com.example.plugin"
        mutation.origin == "plugin 'com.example.plugin'"
    }

    def "attributes collaborative mutation to binary plugin class when it has no id"() {
        def source = new UserCodeSource.Binary(Describables.of("plugin class 'com.example.Plugin'"), "com.example.Plugin", null)
        def mutation = currentMutation(source)

        expect:
        !mutation.source
        mutation.contributor == "com.example.Plugin"
        mutation.origin == "plugin class 'com.example.Plugin'"
    }

    def "attributes collaborative mutation from a script as a source binding"() {
        def source = new UserCodeSource.Script(Describables.of("build file 'build.gradle'"), null)
        def mutation = currentMutation(source)

        expect:
        mutation.source
        mutation.contributor == null
        mutation.origin == "build file 'build.gradle'"
    }

    private CollaborativePropertyMutation currentMutation(UserCodeSource source) {
        def application = Stub(UserCodeApplicationContext.Application) {
            getSource() >> source
        }
        def userCodeContext = Stub(UserCodeApplicationContext) {
            current() >> application
        }
        def options = new DefaultInternalOptions([(ProjectBackedPropertyHost.COLLABORATIVE_PROPERTY_UPDATES_PROPERTY): "true"])
        return new ProjectBackedPropertyHost(project, userCodeContext, options).currentCollaborativeMutation()
    }
}
