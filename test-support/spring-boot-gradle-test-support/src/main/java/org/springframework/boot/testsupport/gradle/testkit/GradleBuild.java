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

package org.springframework.boot.testsupport.gradle.testkit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.JarFile;

import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.util.GradleVersion;

import org.springframework.boot.testsupport.BuildOutput;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.FileSystemUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * A {@code GradleBuild} is used to run a Gradle build using {@link GradleRunner}.
 *
 * @author Andy Wilkinson
 * @author Scott Frederick
 */
public class GradleBuild {

	private final BuildOutput buildOutput;

	private final Dsl dsl;

	private File projectDir;

	private String script;

	private String settings;

	private String gradleVersion;

	private String springBootVersion = "TEST-SNAPSHOT";

	private GradleVersion expectDeprecationWarnings;

	private final List<String> expectedDeprecationMessages = new ArrayList<>();

	private boolean configurationCache;

	private final Map<String, String> scriptProperties = new HashMap<>();

	public GradleBuild(BuildOutput buildOutput) {
		this(buildOutput, Dsl.GROOVY);
	}

	protected GradleBuild(BuildOutput buildOutput, Dsl dsl) {
		this.buildOutput = buildOutput;
		this.dsl = dsl;
	}

	public Dsl getDsl() {
		return this.dsl;
	}

	void before() throws IOException {
		this.projectDir = Files.createTempDirectory("gradle-").toFile();
	}

	void after() {
		this.script = null;
		FileSystemUtils.deleteRecursively(this.projectDir);
	}

	public GradleBuild script(String script) {
		this.script = script.endsWith(this.dsl.getExtension()) ? script : script + this.dsl.getExtension();
		return this;
	}

	public void settings(String settings) {
		this.settings = settings;
	}

	public GradleBuild expectDeprecationWarningsWithAtLeastVersion(String gradleVersion) {
		this.expectDeprecationWarnings = GradleVersion.version(gradleVersion);
		return this;
	}

	public GradleBuild expectDeprecationMessages(String... messages) {
		this.expectedDeprecationMessages.addAll(Arrays.asList(messages));
		return this;
	}

	public GradleBuild configurationCache() {
		this.configurationCache = true;
		return this;
	}

	public boolean isConfigurationCache() {
		return this.configurationCache;
	}

	public GradleBuild scriptProperty(String key, String value) {
		this.scriptProperties.put(key, value);
		return this;
	}

	public GradleBuild scriptPropertyFrom(File propertiesFile, String key) {
		this.scriptProperties.put(key, getProperty(propertiesFile, key));
		return this;
	}

	public boolean gradleVersionIsAtLeast(String version) {
		return GradleVersion.version(this.gradleVersion).compareTo(GradleVersion.version(version)) >= 0;
	}

	public boolean gradleVersionIsLessThan(String version) {
		return GradleVersion.version(this.gradleVersion).compareTo(GradleVersion.version(version)) < 0;
	}

	public BuildResult build(String... arguments) {
		try {
			BuildResult result = prepareRunner(arguments).build();
			if (this.expectDeprecationWarnings == null || (this.gradleVersion != null
					&& this.expectDeprecationWarnings.compareTo(GradleVersion.version(this.gradleVersion)) > 0)) {
				String buildOutput = result.getOutput();
				for (String message : this.expectedDeprecationMessages) {
					buildOutput = buildOutput.replaceAll(message, "");
				}
				assertThat(buildOutput).doesNotContainIgnoringCase("deprecated");
			}
			return result;
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public BuildResult buildAndFail(String... arguments) {
		try {
			return prepareRunner(arguments).buildAndFail();
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	public GradleRunner prepareRunner(String... arguments) throws IOException {
		this.scriptProperties.put("bootVersion", getBootVersion());
		this.scriptProperties.put("dependencyManagementPluginVersion", getDependencyManagementPluginVersion());
		copyTransformedScript(this.script, new File(this.projectDir, "build" + this.dsl.getExtension()));
		if (this.settings != null) {
			copyTransformedScript(this.settings, new File(this.projectDir, "settings.gradle"));
		}
		File repository = new File("src/test/resources/repository");
		if (repository.exists()) {
			FileSystemUtils.copyRecursively(repository, new File(this.projectDir, "repository"));
		}
		GradleRunner gradleRunner = GradleRunner.create().withProjectDir(this.projectDir);
		if (!this.configurationCache) {
			// See https://github.com/gradle/gradle/issues/14125
			gradleRunner.withDebug(true);
		}
		if (this.gradleVersion != null) {
			gradleRunner.withGradleVersion(this.gradleVersion);
		}
		gradleRunner.withTestKitDir(getTestKitDir());
		List<String> allArguments = new ArrayList<>();
		allArguments.add("-PbootVersion=" + getBootVersion());
		allArguments.add("--stacktrace");
		allArguments.addAll(Arrays.asList(arguments));
		allArguments.add("--warning-mode");
		allArguments.add("all");
		if (this.configurationCache) {
			allArguments.add("--configuration-cache");
		}
		return gradleRunner.withArguments(allArguments);
	}

	private void copyTransformedScript(String script, File destination) throws IOException {
		String scriptContent = FileCopyUtils.copyToString(new FileReader(script));
		for (Entry<String, String> property : this.scriptProperties.entrySet()) {
			scriptContent = scriptContent.replace("{" + property.getKey() + "}", property.getValue());
		}
		FileCopyUtils.copy(scriptContent, new FileWriter(destination));
	}

	private File getTestKitDir() {
		File build = this.buildOutput.getRootLocation();
		File testKitRoot = new File(build, "gradle-test-kit");
		String gradleVersion = (this.gradleVersion != null) ? this.gradleVersion : "default";
		return new File(testKitRoot, gradleVersion).getAbsoluteFile();
	}

	public File getProjectDir() {
		return this.projectDir;
	}

	public void setProjectDir(File projectDir) {
		this.projectDir = projectDir;
	}

	public GradleBuild gradleVersion(String version) {
		this.gradleVersion = version;
		return this;
	}

	public String getGradleVersion() {
		return this.gradleVersion;
	}

	public GradleBuild bootVersion(String version) {
		this.springBootVersion = version;
		return this;
	}

	private String getBootVersion() {
		return this.springBootVersion;
	}

	private static String getDependencyManagementPluginVersion() {
		try {
			URL location = DependencyManagementExtension.class.getProtectionDomain().getCodeSource().getLocation();
			try (JarFile jar = new JarFile(new File(location.toURI()))) {
				return jar.getManifest().getMainAttributes().getValue("Implementation-Version");
			}
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to find dependency management plugin version", ex);
		}
	}

	private String getProperty(File propertiesFile, String key) {
		try {
			assertThat(propertiesFile)
				.withFailMessage("Expecting properties file to exist at path '%s'", propertiesFile.getCanonicalFile())
				.exists();
			Properties properties = new Properties();
			try (FileInputStream input = new FileInputStream(propertiesFile)) {
				properties.load(input);
				String value = properties.getProperty(key);
				assertThat(value)
					.withFailMessage("Expecting properties file '%s' to contain the key '%s'",
							propertiesFile.getCanonicalFile(), key)
					.isNotEmpty();
				return value;
			}
		}
		catch (IOException ex) {
			fail("Error reading properties file '" + propertiesFile + "'");
			return null;
		}
	}

}
