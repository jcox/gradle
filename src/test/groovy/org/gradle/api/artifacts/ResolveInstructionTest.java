/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.specs.Specs;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public class ResolveInstructionTest {
    @Test
    public void init() {
        ResolveInstruction resolveInstruction = new ResolveInstruction();
        assertThat(resolveInstruction.isFailOnResolveError(), equalTo(true));
        assertThat(resolveInstruction.isTransitive(), equalTo(true));
        assertThat(resolveInstruction.getDependencySpec(), equalTo(Specs.SATISFIES_ALL));
    }

    @Test
    public void initWithOtherInstance() {
        ResolveInstruction resolveInstructionSource = new ResolveInstruction();
        assertThat(resolveInstructionSource, sameInstance(resolveInstructionSource.setTransitive(false)));
        assertThat(resolveInstructionSource, sameInstance(resolveInstructionSource.setFailOnResolveError(false)));
        assertThat(resolveInstructionSource, sameInstance(resolveInstructionSource.setDependencySpec(HelperUtil.TEST_SEPC)));
        assertThat(new ResolveInstruction(resolveInstructionSource), equalTo(resolveInstructionSource));
    }
}
