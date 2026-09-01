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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.CollaborativePropertyMutation;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.internal.buildoption.InternalOption;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.internal.state.ModelObject;
import org.jspecify.annotations.Nullable;

class ProjectBackedPropertyHost implements PropertyHost {
    static final String COLLABORATIVE_PROPERTY_UPDATES_PROPERTY = "org.gradle.internal.provider.collaborative-property-updates";
    private static final InternalOption<Boolean> COLLABORATIVE_PROPERTY_UPDATES = InternalOptions.ofBoolean(COLLABORATIVE_PROPERTY_UPDATES_PROPERTY, false);

    private final ProjectInternal project;
    @Nullable
    private final UserCodeApplicationContext userCodeApplicationContext;
    private final boolean automaticCollaborativeAttribution;

    public ProjectBackedPropertyHost(ProjectInternal project) {
        this(project, null, false);
    }

    public ProjectBackedPropertyHost(ProjectInternal project, UserCodeApplicationContext userCodeApplicationContext, InternalOptions internalOptions) {
        this(project, userCodeApplicationContext, internalOptions.getBoolean(COLLABORATIVE_PROPERTY_UPDATES));
    }

    private ProjectBackedPropertyHost(ProjectInternal project, @Nullable UserCodeApplicationContext userCodeApplicationContext, boolean automaticCollaborativeAttribution) {
        this.project = project;
        this.userCodeApplicationContext = userCodeApplicationContext;
        this.automaticCollaborativeAttribution = automaticCollaborativeAttribution;
    }

    @Nullable
    @Override
    public CollaborativePropertyMutation currentCollaborativeMutation() {
        if (!automaticCollaborativeAttribution || userCodeApplicationContext == null) {
            return null;
        }

        UserCodeApplicationContext.Application application = userCodeApplicationContext.current();
        if (application == null) {
            return null;
        }

        UserCodeSource source = application.getSource();
        String origin = source.getDisplayName().getDisplayName();
        if (source instanceof UserCodeSource.Binary) {
            UserCodeSource.Binary binary = (UserCodeSource.Binary) source;
            String contributor = binary.getPluginId() != null ? binary.getPluginId() : binary.getClassName();
            return CollaborativePropertyMutation.contributor(contributor, origin);
        }
        if (source instanceof UserCodeSource.Script) {
            return CollaborativePropertyMutation.source(origin);
        }
        return null;
    }

    @Nullable
    @Override
    public String beforeRead(@Nullable ModelObject producer) {
        if (!project.getState().hasCompleted()) {
            return "configuration of " + project.getDisplayName() + " has not completed yet";
        } else if (producer != null) {
            TaskInternal producerTask = (TaskInternal) producer.getTaskThatOwnsThisObject();
            if (producerTask != null && producerTask.getState().isConfigurable()) {
                // Currently cannot tell the difference between access from the producing task and access from outside, so assume
                // all access after the task has started execution is ok
                return producerTask + " has not completed yet";
            }
        }
        return null;
    }
}
