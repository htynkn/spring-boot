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

package org.springframework.boot.websocket.autoconfigure.servlet;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tomcat.websocket.WsWebSocketContainer;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.tomcat.autoconfigure.WebSocketTomcatWebServerFactoryCustomizer;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration;
import org.springframework.boot.websocket.autoconfigure.servlet.WebSocketMessagingAutoConfiguration.WebSocketMessageConverterConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.DelegatingWebSocketMessageBrokerConfiguration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Tests for {@link WebSocketMessagingAutoConfiguration}.
 *
 * @author Andy Wilkinson
 * @author Lasse Wulff
 */
class WebSocketMessagingAutoConfigurationTests {

	private final AnnotationConfigServletWebServerApplicationContext context = new AnnotationConfigServletWebServerApplicationContext();

	private SockJsClient sockJsClient;

	@BeforeEach
	void setup() {
		List<Transport> transports = Arrays.asList(
				new WebSocketTransport(new StandardWebSocketClient(new WsWebSocketContainer())),
				new RestTemplateXhrTransport(new RestTemplate()));
		this.sockJsClient = new SockJsClient(transports);
	}

	@AfterEach
	void tearDown() {
		if (this.context.isActive()) {
			this.context.close();
		}
		this.sockJsClient.stop();
	}

	@Test
	void basicMessagingWithJsonResponse() throws Throwable {
		Object result = performStompSubscription("/app/json");
		JSONAssert.assertEquals("{\"foo\" : 5,\"bar\" : \"baz\"}", new String((byte[]) result), true);
	}

	@Test
	void basicMessagingWithStringResponse() throws Throwable {
		Object result = performStompSubscription("/app/string");
		assertThat(new String((byte[]) result)).isEqualTo("string data");
	}

	@Test
	void whenLazyInitializationIsEnabledThenBasicMessagingWorks() throws Throwable {
		this.context.register(LazyInitializationBeanFactoryPostProcessor.class);
		Object result = performStompSubscription("/app/string");
		assertThat(new String((byte[]) result)).isEqualTo("string data");
	}

	@Test
	void customizedConverterTypesMatchDefaultConverterTypes() {
		List<MessageConverter> customizedConverters = getCustomizedConverters();
		List<MessageConverter> defaultConverters = getDefaultConverters();
		assertThat(customizedConverters).hasSameSizeAs(defaultConverters);
		Iterator<MessageConverter> customizedIterator = customizedConverters.iterator();
		Iterator<MessageConverter> defaultIterator = defaultConverters.iterator();
		while (customizedIterator.hasNext()) {
			assertThat(customizedIterator.next()).isInstanceOf(defaultIterator.next().getClass());
		}
	}

	@Test
	void predefinedThreadExecutorIsSelectedForInboundChannel() {
		AsyncTaskExecutor expectedExecutor = new SimpleAsyncTaskExecutor();
		ChannelRegistration registration = new ChannelRegistration();
		WebSocketMessageConverterConfiguration configuration = new WebSocketMessagingAutoConfiguration.WebSocketMessageConverterConfiguration(
				new ObjectMapper(),
				Map.of(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME, expectedExecutor));
		configuration.configureClientInboundChannel(registration);
		assertThat(registration).extracting("executor").isEqualTo(expectedExecutor);
	}

	@Test
	void predefinedThreadExecutorIsSelectedForOutboundChannel() {
		AsyncTaskExecutor expectedExecutor = new SimpleAsyncTaskExecutor();
		ChannelRegistration registration = new ChannelRegistration();
		WebSocketMessagingAutoConfiguration.WebSocketMessageConverterConfiguration configuration = new WebSocketMessagingAutoConfiguration.WebSocketMessageConverterConfiguration(
				new ObjectMapper(),
				Map.of(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME, expectedExecutor));
		configuration.configureClientOutboundChannel(registration);
		assertThat(registration).extracting("executor").isEqualTo(expectedExecutor);
	}

	@Test
	void webSocketMessageBrokerConfigurerOrdering() throws Throwable {
		TestPropertyValues.of("server.port:0", "spring.jackson.serialization.indent-output:true").applyTo(this.context);
		this.context.register(WebSocketMessagingConfiguration.class, CustomLowWebSocketMessageBrokerConfigurer.class,
				CustomHighWebSocketMessageBrokerConfigurer.class);
		this.context.refresh();
		DelegatingWebSocketMessageBrokerConfiguration delegatingConfiguration = this.context
			.getBean(DelegatingWebSocketMessageBrokerConfiguration.class);
		CustomHighWebSocketMessageBrokerConfigurer high = this.context
			.getBean(CustomHighWebSocketMessageBrokerConfigurer.class);
		WebSocketMessageConverterConfiguration autoConfiguration = this.context
			.getBean(WebSocketMessagingAutoConfiguration.WebSocketMessageConverterConfiguration.class);
		WebSocketMessagingConfiguration configuration = this.context.getBean(WebSocketMessagingConfiguration.class);
		CustomLowWebSocketMessageBrokerConfigurer low = this.context
			.getBean(CustomLowWebSocketMessageBrokerConfigurer.class);
		assertThat(delegatingConfiguration).extracting("configurers")
			.asInstanceOf(InstanceOfAssertFactories.LIST)
			.containsExactly(high, autoConfiguration, configuration, low);
	}

	private List<MessageConverter> getCustomizedConverters() {
		List<MessageConverter> customizedConverters = new ArrayList<>();
		WebSocketMessagingAutoConfiguration.WebSocketMessageConverterConfiguration configuration = new WebSocketMessagingAutoConfiguration.WebSocketMessageConverterConfiguration(
				new ObjectMapper(), Collections.emptyMap());
		configuration.configureMessageConverters(customizedConverters);
		return customizedConverters;
	}

	private List<MessageConverter> getDefaultConverters() {
		DelegatingWebSocketMessageBrokerConfiguration configuration = new DelegatingWebSocketMessageBrokerConfiguration();
		CompositeMessageConverter compositeDefaultConverter = configuration.brokerMessageConverter();
		return compositeDefaultConverter.getConverters();
	}

	private Object performStompSubscription(String topic) throws Throwable {
		TestPropertyValues.of("server.port:0", "spring.jackson.serialization.indent-output:true").applyTo(this.context);
		this.context.register(WebSocketMessagingConfiguration.class);
		this.context.refresh();
		WebSocketStompClient stompClient = new WebSocketStompClient(this.sockJsClient);
		final AtomicReference<Throwable> failure = new AtomicReference<>();
		final AtomicReference<Object> result = new AtomicReference<>();
		final CountDownLatch latch = new CountDownLatch(1);
		StompSessionHandler handler = new StompSessionHandlerAdapter() {

			@Override
			public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
				session.subscribe(topic, new StompFrameHandler() {

					@Override
					public void handleFrame(StompHeaders headers, Object payload) {
						result.set(payload);
						latch.countDown();
					}

					@Override
					public Type getPayloadType(StompHeaders headers) {
						return Object.class;
					}

				});
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				latch.countDown();
			}

			@Override
			public void handleException(StompSession session, StompCommand command, StompHeaders headers,
					byte[] payload, Throwable exception) {
				failure.set(exception);
				latch.countDown();
			}

			@Override
			public void handleTransportError(StompSession session, Throwable exception) {
				failure.set(exception);
				latch.countDown();
			}

		};

		stompClient.setMessageConverter(new SimpleMessageConverter());
		stompClient.connectAsync("ws://localhost:{port}/messaging", handler, this.context.getWebServer().getPort());

		if (!latch.await(30, TimeUnit.SECONDS)) {
			if (failure.get() != null) {
				throw failure.get();
			}
			fail("Response was not received within 30 seconds");
		}
		return result.get();
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebSocket
	@EnableConfigurationProperties
	@EnableWebSocketMessageBroker
	@ImportAutoConfiguration({ JacksonAutoConfiguration.class, TomcatServletWebServerAutoConfiguration.class,
			WebSocketMessagingAutoConfiguration.class, DispatcherServletAutoConfiguration.class })
	static class WebSocketMessagingConfiguration implements WebSocketMessageBrokerConfigurer {

		@Override
		public void registerStompEndpoints(StompEndpointRegistry registry) {

			registry.addEndpoint("/messaging").withSockJS();
		}

		@Override
		public void configureMessageBroker(MessageBrokerRegistry registry) {
			registry.setApplicationDestinationPrefixes("/app");
		}

		@Bean
		MessagingController messagingController() {
			return new MessagingController();
		}

		@Bean
		TomcatServletWebServerFactory tomcat() {
			return new TomcatServletWebServerFactory(0);
		}

		@Bean
		WebSocketTomcatWebServerFactoryCustomizer tomcatCustomizer() {
			return new WebSocketTomcatWebServerFactoryCustomizer();
		}

	}

	@Component
	@Order(Ordered.HIGHEST_PRECEDENCE)
	static class CustomHighWebSocketMessageBrokerConfigurer implements WebSocketMessageBrokerConfigurer {

	}

	@Component
	@Order(Ordered.LOWEST_PRECEDENCE)
	static class CustomLowWebSocketMessageBrokerConfigurer implements WebSocketMessageBrokerConfigurer {

	}

	@Controller
	static class MessagingController {

		@SubscribeMapping("/json")
		Data json() {
			return new Data(5, "baz");
		}

		@SubscribeMapping("/string")
		String string() {
			return "string data";
		}

	}

	public static class Data {

		private final int foo;

		private final String bar;

		Data(int foo, String bar) {
			this.foo = foo;
			this.bar = bar;
		}

		public int getFoo() {
			return this.foo;
		}

		public String getBar() {
			return this.bar;
		}

	}

}
