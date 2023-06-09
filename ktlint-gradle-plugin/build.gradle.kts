/*
 * Copyright (C) 2023 Rick Busarow
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

import builds.VERSION_NAME
import builds.dependsOn
import builds.isRealRootProject
import com.github.gmazzo.gradle.plugins.BuildConfigTask

plugins {
  id("module")
  id("java-gradle-plugin")
  id("com.gradle.plugin-publish")
  `jvm-test-suite`
  alias(libs.plugins.buildconfig)
}

val pluginId = "com.rickbusarow.ktlint"
val pluginArtifactId = "ktlint-gradle-plugin"
val moduleDescription = "the ktlint Gradle plugin"

val pluginDeclaration: NamedDomainObjectProvider<PluginDeclaration> =
  gradlePlugin.plugins
    .register(pluginArtifactId) {
      id = pluginId
      displayName = "ktlint"
      implementationClass = "com.rickbusarow.ktlint.KtLintPlugin"
      version = VERSION_NAME
      description = moduleDescription
      @Suppress("UnstableApiUsage")
      this@register.tags.set(listOf("markdown", "documentation"))
    }

val shade by configurations.register("shadowCompileOnly")

module {

  shadow(shade)

  published(
    artifactId = pluginArtifactId,
    pomDescription = moduleDescription
  )

  publishedPlugin(pluginDeclaration = pluginDeclaration)
}

val deps = mutableSetOf<String>()

buildConfig {

  this@buildConfig.sourceSets.named("main") {

    packageName(builds.GROUP)
    className("BuildConfig")

    buildConfigField("String", "pluginId", "\"$pluginId\"")
    buildConfigField("String", "version", "\"${VERSION_NAME}\"")
    buildConfigField("String", "kotlinVersion", "\"${libs.versions.kotlin.get()}\"")
    buildConfigField(
      type = "String",
      name = "deps",
      value = provider {
        if (deps.isEmpty()) {
          throw GradleException(
            "There are no dependencies to pass along to the Gradle Worker's classpath.  " +
              "Is there a race condition?"
          )
        }
        deps.joinToString(",\" +\n\"", "\"", "\"")
      }
    )
  }

  this@buildConfig.sourceSets.register("integrationTest") {

    packageName(builds.GROUP)
    className("BuildConfig")

    buildConfigField("String", "gradleVersion", "\"${gradle.gradleVersion}\"")
    buildConfigField(
      type = "String",
      name = "gradleUserHomeDir",
      value = "\"${gradle.gradleUserHomeDir.invariantSeparatorsPath}\""
    )
    buildConfigField("String", "kotlinVersion", "\"${libs.versions.kotlin.get()}\"")
    buildConfigField("String", "ktlintVersion", "\"${libs.versions.ktlint.lib.get()}\"")
    buildConfigField("String", "pluginId", "\"$pluginId\"")
    buildConfigField("String", "version", "\"${VERSION_NAME}\"")
  }
}

rootProject.tasks.named("prepareKotlinBuildScriptModel")
  .dependsOn(tasks.withType(BuildConfigTask::class.java))

testing {
  suites {
    @Suppress("UnstableApiUsage")
    register("integrationTest", JvmTestSuite::class) {
      testType.set(TestSuiteType.INTEGRATION_TEST)
    }
  }
}

val main by sourceSets.getting
val test by sourceSets.getting

sourceSets.named("integrationTest") {
  kotlin.apply {
    compileClasspath += main.output
      .plus(test.output)
      .plus(configurations.testRuntimeClasspath.get())
    runtimeClasspath += output + compileClasspath
  }
}

tasks.named("check").dependsOn("integrationTest")
tasks.named("integrationTest").dependsOn("publishToMavenLocalNoDokka")

kotlin {
  val compilations = target.compilations
  compilations.named("integrationTest") {
    associateWith(compilations.getByName("main"))
  }
}

val mainConfig: Configuration = when {
  rootProject.isRealRootProject() -> shade
  else -> configurations.getByName("implementation")
}

fun DependencyHandlerScope.worker(dependencyNotation: Any) {
  mainConfig(dependencyNotation)

  when (dependencyNotation) {
    is org.gradle.api.internal.provider.TransformBackedProvider<*, *> -> {
      deps.add(dependencyNotation.get().toString())
    }

    is ProviderConvertible<*> -> {
      deps.add(dependencyNotation.asProvider().get().toString())
    }

    else -> error("unsupported dependency type -- ${dependencyNotation::class.java.canonicalName}")
  }
}

dependencies {

  compileOnly(gradleApi())

  compileOnly(libs.kotlin.gradle.plugin.api)

  testImplementation(libs.jetbrains.markdown)
  testImplementation(libs.junit.engine)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.junit.params)
  testImplementation(libs.kotest.assertions.api)
  testImplementation(libs.kotest.assertions.core.jvm)
  testImplementation(libs.kotest.assertions.shared)
  testImplementation(libs.kotest.common)
  testImplementation(libs.kotest.extensions)
  testImplementation(libs.kotest.property.jvm)
  testImplementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.serialization.core)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.ktlint.ruleset.standard)
  testImplementation(libs.ktlint.test)

  worker(libs.ec4j.core)
  worker(libs.kotlinx.coroutines.core)
  worker(libs.ktlint.cli.ruleset.core)
  worker(libs.ktlint.rule.engine)
  worker(libs.ktlint.rule.engine.core)
  worker(libs.ktlint.ruleset.standard)
}
