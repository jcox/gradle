/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.tasks.bundling

import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
abstract class AbstractAntSkippableArchiveTest extends AbstractAntArchiveTest {
    File emptyDir

    @Before public void setUp()  {
        super.setUp()
        (emptyDir = new File(testDir, 'emptyDir')).mkdir()
    }

    abstract void executeWithEmptyFileList(boolean createIfEmpty)

    @Test public void testWithEmptyPolicySkip() {
        executeWithEmptyFileList(false)
        assertFalse(new File(testDir, archiveName).exists())
    }

    @Test public void testWithEmptyPolicyCreate() {
        executeWithEmptyFileList(true)
        assertTrue(new File(testDir, archiveName).exists())
    }
}
