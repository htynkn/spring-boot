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

package org.springframework.boot.gradle.plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.util.GradleVersion;
import org.junit.jupiter.api.TestTemplate;

import org.springframework.boot.gradle.junit.GradleCompatibility;
import org.springframework.boot.testsupport.gradle.testkit.GradleBuild;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link JavaPluginAction}.
 *
 * @author Andy Wilkinson
 */
@GradleCompatibility(configurationCache = true)
class JavaPluginActionIntegrationTests {

	GradleBuild gradleBuild;

	@TestTemplate
	void noBootJarTaskWithoutJavaPluginApplied() {
		assertThat(this.gradleBuild.build("tasks").getOutput()).doesNotContain("bootJar");
	}

	@TestTemplate
	void applyingJavaPluginCreatesBootJarTask() {
		assertThat(this.gradleBuild.build("tasks").getOutput()).contains("bootJar");
	}

	@TestTemplate
	void noBootRunTaskWithoutJavaPluginApplied() {
		assertThat(this.gradleBuild.build("tasks").getOutput()).doesNotContain("bootRun");
	}

	@TestTemplate
	void noBootTestRunTaskWithoutJavaPluginApplied() {
		assertThat(this.gradleBuild.build("tasks").getOutput()).doesNotContain("bootTestRun");
	}

	@TestTemplate
	void applyingJavaPluginCreatesBootRunTask() {
		assertThat(this.gradleBuild.build("tasks").getOutput()).contains("bootRun");
	}

	@TestTemplate
	void applyingJavaPluginCreatesBootTestRunTask() {
		assertThat(this.gradleBuild.build("tasks").getOutput()).contains("bootTestRun");
	}

	@TestTemplate
	void javaCompileTasksUseUtf8Encoding() {
		assertThat(this.gradleBuild.build("build").getOutput()).contains("compileJava = UTF-8")
			.contains("compileTestJava = UTF-8");
	}

	@TestTemplate
	void javaCompileTasksUseParametersCompilerFlagByDefault() {
		assertThat(this.gradleBuild.build("build").getOutput()).contains("compileJava compiler args: [-parameters]")
			.contains("compileTestJava compiler args: [-parameters]");
	}

	@TestTemplate
	void javaCompileTasksUseParametersAndAdditionalCompilerFlags() {
		assertThat(this.gradleBuild.build("build").getOutput())
			.contains("compileJava compiler args: [-parameters, -Xlint:all]")
			.contains("compileTestJava compiler args: [-parameters, -Xlint:all]");
	}

	@TestTemplate
	void javaCompileTasksCanOverrideDefaultParametersCompilerFlag() {
		assertThat(this.gradleBuild.build("build").getOutput()).contains("compileJava compiler args: [-Xlint:all]")
			.contains("compileTestJava compiler args: [-Xlint:all]");
	}

	@TestTemplate
	void assembleRunsBootJarAndJar() {
		BuildResult result = this.gradleBuild.build("assemble");
		assertThat(result.task(":bootJar").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
		assertThat(result.task(":jar").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
		File buildLibs = new File(this.gradleBuild.getProjectDir(), "build/libs");
		assertThat(buildLibs.listFiles()).containsExactlyInAnyOrder(
				new File(buildLibs, this.gradleBuild.getProjectDir().getName() + ".jar"),
				new File(buildLibs, this.gradleBuild.getProjectDir().getName() + "-plain.jar"));
	}

	@TestTemplate
	void errorMessageIsHelpfulWhenMainClassCannotBeResolved() {
		BuildResult result = this.gradleBuild.buildAndFail("build", "-PapplyJavaPlugin");
		assertThat(result.task(":bootJar").getOutcome()).isEqualTo(TaskOutcome.FAILED);
		assertThat(result.getOutput()).contains("Main class name has not been configured and it could not be resolved");
	}

	@TestTemplate
	void additionalMetadataLocationsConfiguredWhenProcessorIsPresent() throws IOException {
		createMinimalMainSource();
		File libs = new File(this.gradleBuild.getProjectDir(), "libs");
		libs.mkdirs();
		new JarOutputStream(new FileOutputStream(new File(libs, "spring-boot-configuration-processor-1.2.3.jar")))
			.close();
		BuildResult result = this.gradleBuild.build("compileJava");
		assertThat(result.task(":compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
		assertThat(result.getOutput()).contains("compileJava compiler args: [-parameters, -Aorg.springframework.boot."
				+ "configurationprocessor.additionalMetadataLocations="
				+ new File(this.gradleBuild.getProjectDir(), "src/main/resources").getCanonicalPath());
	}

	@TestTemplate
	void additionalMetadataLocationsNotConfiguredWhenProcessorIsAbsent() throws IOException {
		createMinimalMainSource();
		BuildResult result = this.gradleBuild.build("compileJava");
		assertThat(result.task(":compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
		assertThat(result.getOutput()).contains("compileJava compiler args: [-parameters]");
	}

	@TestTemplate
	void applyingJavaPluginCreatesDevelopmentOnlyConfiguration() {
		assertThat(this.gradleBuild.build("help").getOutput()).contains("developmentOnly exists = true");
	}

	@TestTemplate
	void applyingJavaPluginCreatesTestAndDevelopmentOnlyConfiguration() {
		assertThat(this.gradleBuild.build("help").getOutput()).contains("testAndDevelopmentOnly exists = true");
	}

	@TestTemplate
	void testCompileClasspathIncludesTestAndDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).contains("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void testRuntimeClasspathIncludesTestAndDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).contains("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void testCompileClasspathDoesNotIncludeDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).doesNotContain("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void testRuntimeClasspathDoesNotIncludeDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).doesNotContain("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void compileClasspathDoesNotIncludeTestAndDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).doesNotContain("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void runtimeClasspathIncludesTestAndDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).contains("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void compileClasspathDoesNotIncludeDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).doesNotContain("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void runtimeClasspathIncludesDevelopmentOnlyDependencies() {
		assertThat(this.gradleBuild.build("help").getOutput()).contains("commons-lang3-3.12.0.jar");
	}

	@TestTemplate
	void productionRuntimeClasspathIsConfiguredWithAttributesThatMatchRuntimeClasspath() {
		String output = this.gradleBuild.build("build").getOutput();
		Matcher matcher = Pattern.compile("runtimeClasspath: (\\[.*])").matcher(output);
		assertThat(matcher.find()).as("%s found in %s", matcher, output).isTrue();
		String attributes = matcher.group(1);
		assertThat(output).contains("productionRuntimeClasspath: " + attributes);
	}

	@TestTemplate
	void productionRuntimeClasspathIsConfiguredWithResolvabilityAndConsumabilityThatMatchesRuntimeClasspath() {
		String output = this.gradleBuild.build("build").getOutput();
		assertThat(output).contains("runtimeClasspath canBeResolved: true");
		assertThat(output).contains("runtimeClasspath canBeConsumed: false");
		assertThat(output).contains("productionRuntimeClasspath canBeResolved: true");
		assertThat(output).contains("productionRuntimeClasspath canBeConsumed: false");
	}

	@TestTemplate
	void taskConfigurationIsAvoided() throws IOException {
		BuildResult result = this.gradleBuild.build("help");
		String output = result.getOutput();
		BufferedReader reader = new BufferedReader(new StringReader(output));
		String line;
		Set<String> configured = new HashSet<>();
		while ((line = reader.readLine()) != null) {
			if (line.startsWith("Configuring :")) {
				configured.add(line.substring("Configuring :".length()));
			}
		}
		if (!this.gradleBuild.isConfigurationCache() && GradleVersion.version(this.gradleBuild.getGradleVersion())
			.compareTo(GradleVersion.version("7.3.3")) < 0) {
			assertThat(configured).containsExactly("help");
		}
		else {
			assertThat(configured).containsExactlyInAnyOrder("help", "clean");
		}
	}

	private void createMinimalMainSource() throws IOException {
		File examplePackage = new File(this.gradleBuild.getProjectDir(), "src/main/java/com/example");
		examplePackage.mkdirs();
		new File(examplePackage, "Application.java").createNewFile();
	}

}
