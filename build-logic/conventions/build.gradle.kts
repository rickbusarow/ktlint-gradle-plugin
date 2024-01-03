/*
 * Copyright (C) 2024 Rick Busarow
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

plugins {
  kotlin("jvm")
  alias(libs.plugins.google.ksp)
  alias(libs.plugins.ktlint)
  id("java-gradle-plugin")
}

gradlePlugin {
  plugins {
    create("ben-manes") {
      id = "mcbuild.ben-manes"
      implementationClass = "builds.BenManesVersionsPlugin"
    }
    create("builds.check") {
      id = "builds.check"
      implementationClass = "builds.CheckPlugin"
    }
    create("builds.clean") {
      id = "builds.clean"
      implementationClass = "builds.CleanPlugin"
    }
    create("builds.dependency-guard") {
      id = "builds.dependency-guard"
      implementationClass = "builds.DependencyGuardConventionPlugin"
    }
    create("builds.detekt") {
      id = "builds.detekt"
      implementationClass = "builds.DetektConventionPlugin"
    }
    create("builds.dokka") {
      id = "builds.dokka"
      implementationClass = "builds.DokkaConventionPlugin"
    }
    create("builds.integration-tests") {
      id = "builds.integration-tests"
      implementationClass = "builds.IntegrationTestsConventionPlugin"
    }
    create("builds.kotlin") {
      id = "builds.kotlin"
      implementationClass = "builds.KotlinJvmConventionPlugin"
    }
    create("builds.knit") {
      id = "builds.knit"
      implementationClass = "builds.KnitConventionPlugin"
    }
    create("builds.ktlint") {
      id = "builds.ktlint"
      implementationClass = "builds.KtLintConventionPlugin"
    }
    create("builds.spotless") {
      id = "builds.spotless"
      implementationClass = "builds.SpotlessConventionPlugin"
    }
    create("builds.test") {
      id = "builds.test"
      implementationClass = "builds.TestConventionPlugin"
    }
  }
}

dependencies {

  api(libs.rickBusarow.kgx)
  api(libs.rickBusarow.ktrules)
  api(libs.slf4j.api)
  api(libs.square.kotlinPoet)

  api(project(":core"))

  implementation(libs.benManes.versions)
  implementation(libs.detekt.gradle)
  implementation(libs.diffplug.spotless)
  implementation(libs.dokka.gradle)
  implementation(libs.dokka.versioning)
  implementation(libs.dropbox.dependencyGuard)
  implementation(libs.github.release)
  implementation(libs.google.ksp)
  implementation(libs.gradle.plugin.publish)
  implementation(libs.jetbrains.markdown)
  implementation(libs.johnrengelman.shadowJar)
  implementation(libs.kotlin.compiler)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.kotlin.reflect)
  implementation(libs.kotlinx.binaryCompatibility)
  implementation(libs.kotlinx.knit)
  implementation(libs.rickBusarow.ktlint)
  implementation(libs.rickBusarow.moduleCheck.gradle.plugin) {
    exclude(group = "org.jetbrains.kotlin")
  }
  implementation(libs.vanniktech.publish)
}
