/*
 * Copyright 2012-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.context.reactive;

import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@link Environment} implementation to be used by {@code Reactive}-based web
 * applications. All web-related (reactive-based) {@code ApplicationContext} classes
 * initialize an instance by default.
 *
 * @author Phillip Webb
 * @since 2.0.0
 */
public class StandardReactiveWebEnvironment extends StandardEnvironment implements ConfigurableReactiveWebEnvironment {

	public StandardReactiveWebEnvironment() {
		super();
	}

	protected StandardReactiveWebEnvironment(MutablePropertySources propertySources) {
		super(propertySources);
	}

}
