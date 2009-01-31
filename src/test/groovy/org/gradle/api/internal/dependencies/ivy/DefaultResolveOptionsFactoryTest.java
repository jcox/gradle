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
package org.gradle.api.internal.dependencies.ivy;

import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.hamcrest.Matchers;
import org.gradle.util.WrapUtil;

/**
 * @author Hans Dockter
 */
public class DefaultResolveOptionsFactoryTest {
    private static final String TEST_CONF = "conf1";

    @Test
    public void createResolveOptions() {
        ResolveOptions resolveOptions = new DefaultResolveOptionsFactory().createResolveOptions(TEST_CONF, null);
        assertThat(resolveOptions.getConfs(), Matchers.equalTo(WrapUtil.toArray(TEST_CONF)));
    }
}
