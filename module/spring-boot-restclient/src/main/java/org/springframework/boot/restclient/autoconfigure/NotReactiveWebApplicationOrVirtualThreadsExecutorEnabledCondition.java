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

package org.springframework.boot.restclient.autoconfigure;

import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Conditional;

/**
 * {@link SpringBootCondition} that applies when running in a non-reactive web application
 * or virtual threads are enabled.
 *
 * @author Dmitry Sulman
 */
class NotReactiveWebApplicationOrVirtualThreadsExecutorEnabledCondition extends AnyNestedCondition {

	NotReactiveWebApplicationOrVirtualThreadsExecutorEnabledCondition() {
		super(ConfigurationPhase.REGISTER_BEAN);
	}

	@Conditional(NotReactiveWebApplicationCondition.class)
	private static final class NotReactiveWebApplication {

	}

	@ConditionalOnThreading(Threading.VIRTUAL)
	@ConditionalOnBean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
	private static final class VirtualThreadsExecutorEnabled {

	}

}
