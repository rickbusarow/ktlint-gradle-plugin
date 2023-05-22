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

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  id("module")
  id("java-gradle-plugin")
  id("com.gradle.plugin-publish")
  @Suppress("DSL_SCOPE_VIOLATION")
  alias(libs.plugins.integration.test)
  alias(libs.plugins.buildconfig)
  idea
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
      this@register.tags.set(listOf("markdown", "documentation"))
    }

val shade by configurations.register("shadowCompileOnly")

module {
  autoService()
  serialization()
  shadow(shade)

  published(
    artifactId = pluginArtifactId,
    pomDescription = moduleDescription
  )

  publishedPlugin(pluginDeclaration = pluginDeclaration)
}

buildConfig {

  this@buildConfig.sourceSets.named(java.sourceSets.integration.name) {

    this@named.packageName(builds.GROUP)
    this@named.className("BuildConfig")

    this@named.buildConfigField("String", "pluginId", "\"$pluginId\"")
    this@named.buildConfigField("String", "version", "\"${VERSION_NAME}\"")
    this@named.buildConfigField("String", "kotlinVersion", "\"${libs.versions.kotlin.get()}\"")
  }
}

rootProject.tasks.named("prepareKotlinBuildScriptModel") {
  dependsOn(tasks.withType(BuildConfigTask::class.java))
}

idea {
  module {
    java.sourceSets.integration {
      @Suppress("UnstableApiUsage")
      this@module.testSources.from(allSource.srcDirs)
    }
  }
}

val mainConfig: Configuration = if (rootProject.isRealRootProject()) {
  shade
} else {
  configurations.getByName("implementation")
}

dependencies {

  api(libs.ktlint.core)
  api(libs.ktlint.ruleset.standard)

  compileOnly(gradleApi())

  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.kotlin.gradle.plugin.api)

  implementation(libs.google.auto.service.annotations)
  implementation(libs.jetbrains.markdown)
  implementation(libs.jmailen.kotlinter)
  implementation(libs.kotlin.compiler)
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlinx.coroutines.core)

  mainConfig(libs.ec4j.core)
  mainConfig(libs.jetbrains.markdown)
  mainConfig(libs.jetbrains.markdown)
  mainConfig(libs.kotlinx.coroutines.core)
  mainConfig(libs.kotlinx.serialization.core)
  mainConfig(libs.kotlinx.serialization.json)
  mainConfig(libs.ktlint.cli.ruleset.core)
  mainConfig(libs.ktlint.rule.engine)
  mainConfig(libs.ktlint.rule.engine.core)

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
  testImplementation(libs.ktlint.test)
}

tasks.named("integrationTest").dependsOn("publishToMavenLocalNoDokka")
