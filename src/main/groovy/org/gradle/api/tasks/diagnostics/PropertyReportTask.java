/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The {@code PropertyListTask} prints out the properties of a project, and its sub-projects and tasks. This task is
 * used when you execute the property list command-line option.
 */
public class PropertyReportTask extends AbstractReportTask {
    private PropertyReportRenderer renderer = new PropertyReportRenderer();

    public PropertyReportTask(Project project, String name) {
        super(project, name);
    }

    public PropertyReportRenderer getRenderer() {
        return renderer;
    }

    public void setRenderer(PropertyReportRenderer renderer) {
        this.renderer = renderer;
    }

    public void generate(Project project) throws IOException {
        for (Map.Entry<String, ?> entry : new TreeMap<String, Object>(project.getProperties()).entrySet()) {
            if (entry.getKey().equals("properties")) {
                renderer.addProperty(entry.getKey(), "{...}");
            } else {
                renderer.addProperty(entry.getKey(), entry.getValue());
            }
        }
    }
}
