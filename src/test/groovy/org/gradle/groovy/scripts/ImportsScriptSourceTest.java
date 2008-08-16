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
package org.gradle.groovy.scripts;

import org.junit.Test;
import org.junit.Assert;
import org.junit.Before;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.gradle.api.internal.project.ImportsReader;

import java.io.File;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class ImportsScriptSourceTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final File rootDir = new File("rootDir");
    private ScriptSource backingSource;
    private ImportsReader importsReader;
    private ImportsScriptSource source;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        backingSource = context.mock(ScriptSource.class);
        importsReader = context.mock(ImportsReader.class);
        source = new ImportsScriptSource(backingSource, importsReader, rootDir);
    }
    
    @Test
    public void prependsImportsToScriptText() {
        context.checking(new Expectations() {
            {
                one(backingSource).getText();
                will(returnValue("<content>"));
                one(importsReader).getImports(rootDir);
                will(returnValue("<imports>"));
            }
        });

        assertThat(source.getText(), equalTo("<imports>\n<content>"));
    }

    @Test
    public void doesNotPrependImportsWhenScriptHasNoText() {
        context.checking(new Expectations(){{
            one(backingSource).getText();
            will(returnValue(null));
        }});

        assertThat(source.getText(), nullValue());
    }

    @Test
    public void delegatesAllOtherMethodsToBackingScriptSource() {
        context.checking(new Expectations(){{
            one(backingSource).getClassName();
            will(returnValue("classname"));

            one(backingSource).getDescription();
            will(returnValue("description"));

            one(backingSource).getSourceFile();
            will(returnValue(new File("sourceFile")));
        }});

        assertThat(source.getClassName(), equalTo("classname"));
        assertThat(source.getDescription(), equalTo("description"));
        assertThat(source.getSourceFile(), equalTo(new File("sourceFile")));
    }
}
