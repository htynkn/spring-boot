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

package org.springframework.boot.activemq.docker.compose;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.activemq.autoconfigure.ActiveMQConnectionDetails;
import org.springframework.boot.docker.compose.core.RunningService;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionDetailsFactory;
import org.springframework.boot.docker.compose.service.connection.DockerComposeConnectionSource;

/**
 * {@link DockerComposeConnectionDetailsFactory} to create
 * {@link ActiveMQConnectionDetails} for an {@code activemq} service.
 *
 * @author Stephane Nicoll
 * @author Eddú Meléndez
 */
class ActiveMQClassicDockerComposeConnectionDetailsFactory
		extends DockerComposeConnectionDetailsFactory<ActiveMQConnectionDetails> {

	private static final int ACTIVEMQ_PORT = 61616;

	protected ActiveMQClassicDockerComposeConnectionDetailsFactory() {
		super("apache/activemq-classic");
	}

	@Override
	protected ActiveMQConnectionDetails getDockerComposeConnectionDetails(DockerComposeConnectionSource source) {
		return new ActiveMQDockerComposeConnectionDetails(source.getRunningService());
	}

	/**
	 * {@link ActiveMQConnectionDetails} backed by an {@code activemq}
	 * {@link RunningService}.
	 */
	static class ActiveMQDockerComposeConnectionDetails extends DockerComposeConnectionDetails
			implements ActiveMQConnectionDetails {

		private final ActiveMQClassicEnvironment environment;

		private final String brokerUrl;

		protected ActiveMQDockerComposeConnectionDetails(RunningService service) {
			super(service);
			this.environment = new ActiveMQClassicEnvironment(service.env());
			this.brokerUrl = "tcp://" + service.host() + ":" + service.ports().get(ACTIVEMQ_PORT);
		}

		@Override
		public String getBrokerUrl() {
			return this.brokerUrl;
		}

		@Override
		public @Nullable String getUser() {
			return this.environment.getUser();
		}

		@Override
		public @Nullable String getPassword() {
			return this.environment.getPassword();
		}

	}

}
