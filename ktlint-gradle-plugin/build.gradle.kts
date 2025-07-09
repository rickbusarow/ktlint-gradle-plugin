/*
 * Copyright (C) 2025 Rick Busarow
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

import com.rickbusarow.kgx.isRealRootProject

plugins {
  alias(libs.plugins.mahout.java.gradle.plugin)
  alias(libs.plugins.mahout.gradle.test)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.gradle.plugin.publish)
}

val pluginId = "com.rickbusarow.ktlint"
val pluginArtifactId = "ktlint-gradle-plugin"
val moduleDescription = "An incremental Ktlint Gradle wrapper with pretty logging"

val pluginDeclaration: NamedDomainObjectProvider<PluginDeclaration> =
  gradlePlugin.plugins
    .register(pluginArtifactId) {
      id = pluginId
      displayName = "ktlint"
      implementationClass = "com.rickbusarow.ktlint.KtLintPlugin"
      description = moduleDescription
      @Suppress("UnstableApiUsage")
      this@register.tags.set(listOf("ktlint", "kotlin"))
    }

mahout {

  publishing {
    publishPlugin(pluginDeclaration)
  }
  gradleTests {}
}

val deps = objects.setProperty<String>()
val ktlintDeps = objects.setProperty<String>()

buildConfig {

  sourceSets.named("main") {

    packageName(mahoutProperties.group.get())
    className("BuildConfig")

    useKotlinOutput {
      internalVisibility = true
    }

    buildConfigField("version", mahoutProperties.versionName)
    buildConfigField("kotlinVersion", libs.versions.kotlin)
    buildConfigField("ktlintVersion", libs.versions.ktlint.lib)
    buildConfigField("pluginId", pluginId)
    buildConfigField("deps", deps)
    buildConfigField("ktlintDeps", ktlintDeps)
  }

  this@buildConfig.sourceSets.named(mahout.gradleTests.sourceSetName.get()) {

    packageName(mahoutProperties.group.get())
    className("GradleTestBuildConfig")

    useKotlinOutput {
      internalVisibility = true
    }

    buildConfigField("localBuildM2Dir", mahout.gradleTests.gradleTestM2Dir.asFile)

    buildConfigField("pluginId", pluginId)
    buildConfigField("version", mahoutProperties.versionName)
    buildConfigField("kotlinVersion", libs.versions.kotlin)
    buildConfigField("ktlintVersion", libs.versions.ktlint.lib)
  }
}

val mainConfig: Configuration = when {
  rootProject.isRealRootProject() -> configurations.compileOnly.get()
  else -> configurations.getByName("implementation")
}

fun Any.asExternalDependency(): ExternalDependency {
  return when (this) {
    is ExternalDependency -> this
    is org.gradle.api.internal.provider.TransformBackedProvider<*, *> -> this.get() as ExternalDependency
    is ProviderConvertible<*> -> this.asProvider().get() as ExternalDependency
    else -> error("unsupported dependency type -- ${this::class.java.canonicalName}")
  }
}

fun DependencyHandlerScope.worker(dependencyNotation: Any) {
  mainConfig(dependencyNotation)

  val dependency = dependencyNotation.asExternalDependency()
  if (dependency.group == libs.ktlint.core.get().group) {
    ktlintDeps.add(dependency.module.toString())
  } else {
    deps.add(dependency.toString())
  }
}

dependencies {

  compileOnly(gradleApi())

  implementation(libs.rickBusarow.kgx)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.junit.jupiter.api)
  testImplementation(libs.kotest.assertions.api)
  testImplementation(libs.kotest.assertions.shared)
  testImplementation(libs.kotest.common)
  testImplementation(libs.kotest.extensions)
  testImplementation(libs.kotest.property.jvm)
  testImplementation(libs.ktlint.ruleset.standard)
  testImplementation(libs.ktlint.test)

  gradleTestImplementation(libs.junit.jupiter)
  gradleTestImplementation(libs.junit.jupiter.api)
  gradleTestImplementation(libs.kotest.assertions.api)
  gradleTestImplementation(libs.rickBusarow.kase)
  gradleTestImplementation(libs.rickBusarow.kase.gradle)
  gradleTestImplementation(libs.rickBusarow.kase.gradle.dsl)
  gradleTestImplementation(libs.kotest.assertions.core.jvm)
  gradleTestImplementation(libs.kotest.assertions.shared)
  gradleTestImplementation(libs.kotest.common)
  gradleTestImplementation(libs.kotest.extensions)
  gradleTestImplementation(libs.ktlint.ruleset.standard)
  gradleTestImplementation(libs.ktlint.test)

  worker(libs.ec4j.core)
  worker(libs.kotlin.gradle.plugin)
  worker(libs.kotlin.gradle.plugin.api)
  worker(libs.ktlint.cli.ruleset.core)
  worker(libs.ktlint.rule.engine)
  worker(libs.ktlint.rule.engine.core)
  worker(libs.ktlint.ruleset.standard)
}
