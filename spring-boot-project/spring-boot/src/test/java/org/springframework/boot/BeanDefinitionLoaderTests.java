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

package org.springframework.boot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sampleconfig.MyComponentInPackageWithoutDot;

import org.springframework.boot.sampleconfig.MyComponent;
import org.springframework.boot.sampleconfig.MyNamedComponent;
import org.springframework.boot.testsupport.classpath.resources.WithResource;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BeanDefinitionLoader}.
 *
 * @author Phillip Webb
 * @author Vladislav Kisel
 */
class BeanDefinitionLoaderTests {

	private StaticApplicationContext registry;

	@BeforeEach
	void setup() {
		this.registry = new StaticApplicationContext();
	}

	@AfterEach
	void cleanUp() {
		this.registry.close();
	}

	@Test
	void loadClass() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, MyComponent.class);
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myComponent")).isTrue();
	}

	@Test
	void anonymousClassNotLoaded() {
		MyComponent myComponent = new MyComponent() {

		};
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, myComponent.getClass());
		assertThat(load(loader)).isZero();
	}

	@Test
	void loadJsr330Class() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, MyNamedComponent.class);
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myNamedComponent")).isTrue();
	}

	@Test
	@WithSampleBeansXmlResource
	void loadXmlResource() {
		ClassPathResource resource = new ClassPathResource("org/springframework/boot/sample-beans.xml");
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, resource);
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myXmlComponent")).isTrue();

	}

	@Test
	@WithResource(name = "org/springframework/boot/sample-beans.groovy", content = """
			import org.springframework.boot.sampleconfig.MyComponent;

			beans {
				myGroovyComponent(MyComponent) {}
			}
			""")
	void loadGroovyResource() {
		ClassPathResource resource = new ClassPathResource("org/springframework/boot/sample-beans.groovy");
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, resource);
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myGroovyComponent")).isTrue();

	}

	@Test
	@WithResource(name = "org/springframework/boot/sample-namespace.groovy", content = """
			import org.springframework.boot.sampleconfig.MyComponent;

			beans {
				xmlns([ctx:'http://www.springframework.org/schema/context'])
				ctx.'component-scan'('base-package':'nonexistent')
				myGroovyComponent(MyComponent) {}
			}
			""")
	void loadGroovyResourceWithNamespace() {
		ClassPathResource resource = new ClassPathResource("org/springframework/boot/sample-namespace.groovy");
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, resource);
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myGroovyComponent")).isTrue();

	}

	@Test
	void loadPackage() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, MyComponent.class.getPackage());
		assertThat(load(loader)).isEqualTo(2);
		assertThat(this.registry.containsBean("myComponent")).isTrue();
		assertThat(this.registry.containsBean("myNamedComponent")).isTrue();
	}

	@Test
	void loadClassName() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, MyComponent.class.getName());
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myComponent")).isTrue();
	}

	@Test
	@WithSampleBeansXmlResource
	void loadXmlName() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry,
				"classpath:org/springframework/boot/sample-beans.xml");
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myXmlComponent")).isTrue();
	}

	@Test
	@WithResource(name = "org/springframework/boot/sample-beans.groovy", content = """
			import org.springframework.boot.sampleconfig.MyComponent;

			beans {
				myGroovyComponent(MyComponent) {}
			}
			""")
	void loadGroovyName() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry,
				"classpath:org/springframework/boot/sample-beans.groovy");
		assertThat(load(loader)).isOne();
		assertThat(this.registry.containsBean("myGroovyComponent")).isTrue();
	}

	@Test
	void loadPackageName() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, MyComponent.class.getPackage().getName());
		assertThat(load(loader)).isEqualTo(2);
		assertThat(this.registry.containsBean("myComponent")).isTrue();
		assertThat(this.registry.containsBean("myNamedComponent")).isTrue();
	}

	@Test
	void loadPackageNameWithoutDot() {
		// See gh-6126
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry,
				MyComponentInPackageWithoutDot.class.getPackage().getName());
		int loaded = load(loader);
		assertThat(loaded).isOne();
		assertThat(this.registry.containsBean("myComponentInPackageWithoutDot")).isTrue();
	}

	@Test
	void loadPackageAndClassDoesNotDoubleAdd() {
		BeanDefinitionLoader loader = new BeanDefinitionLoader(this.registry, MyComponent.class.getPackage(),
				MyComponent.class);
		assertThat(load(loader)).isEqualTo(2);
		assertThat(this.registry.containsBean("myComponent")).isTrue();
		assertThat(this.registry.containsBean("myNamedComponent")).isTrue();
	}

	private int load(BeanDefinitionLoader loader) {
		int beans = this.registry.getBeanDefinitionCount();
		loader.load();
		return this.registry.getBeanDefinitionCount() - beans;
	}

}
