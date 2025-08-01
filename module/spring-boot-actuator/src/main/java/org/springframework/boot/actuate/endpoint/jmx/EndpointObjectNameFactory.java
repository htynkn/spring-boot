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

package org.springframework.boot.actuate.endpoint.jmx;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * A factory to create an {@link ObjectName} for an {@link EndpointMBean}.
 *
 * @author Stephane Nicoll
 * @since 2.0.0
 */
@FunctionalInterface
public interface EndpointObjectNameFactory {

	/**
	 * Generate an {@link ObjectName} for the specified {@link ExposableJmxEndpoint
	 * endpoint}.
	 * @param endpoint the endpoint MBean to handle
	 * @return the {@link ObjectName} to use for the endpoint
	 * @throws MalformedObjectNameException if the object name is invalid
	 */
	ObjectName getObjectName(ExposableJmxEndpoint endpoint) throws MalformedObjectNameException;

}
