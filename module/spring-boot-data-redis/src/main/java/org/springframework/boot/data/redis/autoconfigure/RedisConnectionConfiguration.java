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

package org.springframework.boot.data.redis.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.data.redis.autoconfigure.RedisConnectionDetails.Cluster;
import org.springframework.boot.data.redis.autoconfigure.RedisConnectionDetails.Node;
import org.springframework.boot.data.redis.autoconfigure.RedisConnectionDetails.Sentinel;
import org.springframework.boot.data.redis.autoconfigure.RedisConnectionDetails.Standalone;
import org.springframework.boot.data.redis.autoconfigure.RedisProperties.Pool;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * Base Redis connection configuration.
 *
 * @author Mark Paluch
 * @author Stephane Nicoll
 * @author Alen Turkovic
 * @author Scott Frederick
 * @author Eddú Meléndez
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @author Yanming Zhou
 */
abstract class RedisConnectionConfiguration {

	private static final boolean COMMONS_POOL2_AVAILABLE = ClassUtils.isPresent("org.apache.commons.pool2.ObjectPool",
			RedisConnectionConfiguration.class.getClassLoader());

	private final RedisProperties properties;

	private final @Nullable RedisStandaloneConfiguration standaloneConfiguration;

	private final @Nullable RedisSentinelConfiguration sentinelConfiguration;

	private final @Nullable RedisClusterConfiguration clusterConfiguration;

	private final RedisConnectionDetails connectionDetails;

	protected final Mode mode;

	protected RedisConnectionConfiguration(RedisProperties properties, RedisConnectionDetails connectionDetails,
			ObjectProvider<RedisStandaloneConfiguration> standaloneConfigurationProvider,
			ObjectProvider<RedisSentinelConfiguration> sentinelConfigurationProvider,
			ObjectProvider<RedisClusterConfiguration> clusterConfigurationProvider) {
		this.properties = properties;
		this.standaloneConfiguration = standaloneConfigurationProvider.getIfAvailable();
		this.sentinelConfiguration = sentinelConfigurationProvider.getIfAvailable();
		this.clusterConfiguration = clusterConfigurationProvider.getIfAvailable();
		this.connectionDetails = connectionDetails;
		this.mode = determineMode();
	}

	protected final RedisStandaloneConfiguration getStandaloneConfig() {
		if (this.standaloneConfiguration != null) {
			return this.standaloneConfiguration;
		}
		RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
		Standalone standalone = this.connectionDetails.getStandalone();
		Assert.state(standalone != null, "'standalone' must not be null");
		config.setHostName(standalone.getHost());
		config.setPort(standalone.getPort());
		config.setUsername(this.connectionDetails.getUsername());
		config.setPassword(RedisPassword.of(this.connectionDetails.getPassword()));
		config.setDatabase(standalone.getDatabase());
		return config;
	}

	protected final @Nullable RedisSentinelConfiguration getSentinelConfig() {
		if (this.sentinelConfiguration != null) {
			return this.sentinelConfiguration;
		}
		if (this.connectionDetails.getSentinel() != null) {
			RedisSentinelConfiguration config = new RedisSentinelConfiguration();
			config.master(this.connectionDetails.getSentinel().getMaster());
			config.setSentinels(createSentinels(this.connectionDetails.getSentinel()));
			config.setUsername(this.connectionDetails.getUsername());
			String password = this.connectionDetails.getPassword();
			if (password != null) {
				config.setPassword(RedisPassword.of(password));
			}
			config.setSentinelUsername(this.connectionDetails.getSentinel().getUsername());
			String sentinelPassword = this.connectionDetails.getSentinel().getPassword();
			if (sentinelPassword != null) {
				config.setSentinelPassword(RedisPassword.of(sentinelPassword));
			}
			config.setDatabase(this.connectionDetails.getSentinel().getDatabase());
			return config;
		}
		return null;
	}

	/**
	 * Create a {@link RedisClusterConfiguration} if necessary.
	 * @return {@literal null} if no cluster settings are set.
	 */
	protected final @Nullable RedisClusterConfiguration getClusterConfiguration() {
		if (this.clusterConfiguration != null) {
			return this.clusterConfiguration;
		}
		RedisProperties.Cluster clusterProperties = this.properties.getCluster();
		if (this.connectionDetails.getCluster() != null) {
			RedisClusterConfiguration config = new RedisClusterConfiguration();
			config.setClusterNodes(getNodes(this.connectionDetails.getCluster()));
			if (clusterProperties != null && clusterProperties.getMaxRedirects() != null) {
				config.setMaxRedirects(clusterProperties.getMaxRedirects());
			}
			config.setUsername(this.connectionDetails.getUsername());
			String password = this.connectionDetails.getPassword();
			if (password != null) {
				config.setPassword(RedisPassword.of(password));
			}
			return config;
		}
		return null;
	}

	private List<RedisNode> getNodes(Cluster cluster) {
		return cluster.getNodes().stream().map(this::asRedisNode).toList();
	}

	private RedisNode asRedisNode(Node node) {
		return new RedisNode(node.host(), node.port());
	}

	protected final RedisProperties getProperties() {
		return this.properties;
	}

	protected @Nullable SslBundle getSslBundle() {
		return switch (this.mode) {
			case STANDALONE -> (this.connectionDetails.getStandalone() != null)
					? this.connectionDetails.getStandalone().getSslBundle() : null;
			case CLUSTER -> (this.connectionDetails.getCluster() != null)
					? this.connectionDetails.getCluster().getSslBundle() : null;
			case SENTINEL -> (this.connectionDetails.getSentinel() != null)
					? this.connectionDetails.getSentinel().getSslBundle() : null;
		};
	}

	protected final boolean isSslEnabled() {
		return getProperties().getSsl().isEnabled();
	}

	protected final boolean urlUsesSsl(String url) {
		return RedisUrl.of(url).useSsl();
	}

	protected boolean isPoolEnabled(Pool pool) {
		Boolean enabled = pool.getEnabled();
		return (enabled != null) ? enabled : COMMONS_POOL2_AVAILABLE;
	}

	private List<RedisNode> createSentinels(Sentinel sentinel) {
		List<RedisNode> nodes = new ArrayList<>();
		for (Node node : sentinel.getNodes()) {
			nodes.add(asRedisNode(node));
		}
		return nodes;
	}

	protected final RedisConnectionDetails getConnectionDetails() {
		return this.connectionDetails;
	}

	private Mode determineMode() {
		if (getSentinelConfig() != null) {
			return Mode.SENTINEL;
		}
		if (getClusterConfiguration() != null) {
			return Mode.CLUSTER;
		}
		return Mode.STANDALONE;
	}

	enum Mode {

		STANDALONE, CLUSTER, SENTINEL

	}

}
