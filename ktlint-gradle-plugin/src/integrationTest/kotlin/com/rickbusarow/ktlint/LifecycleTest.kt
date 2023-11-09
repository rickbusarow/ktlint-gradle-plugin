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

package com.rickbusarow.ktlint

import com.rickbusarow.kase.gradle.BaseGradleTest
import com.rickbusarow.kase.gradle.DependencyVersion.Gradle
import com.rickbusarow.kase.gradle.DependencyVersion.Kotlin
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.GradleTestEnvironment
import com.rickbusarow.kase.gradle.KaseTestFactory
import com.rickbusarow.kase.gradle.VersionMatrix
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.TestFactory

// internal class LifecycleTest : BaseGradleTest, TestEnvironmentFactory<KtLintTestEnvironment> {
internal class LifecycleTest :
  BaseGradleTest<GradleKotlinTestVersions>,
  KaseTestFactory<GradleTestEnvironment<GradleKotlinTestVersions>, GradleKotlinTestVersions> {

  val gradleList = listOf(Gradle("8.3"), Gradle("8.4"))
  val kotlinList = listOf(
    Kotlin("1.8.10"),
    Kotlin("1.8.22"),
    Kotlin("1.9.0"),
    Kotlin("1.9.10"),
    Kotlin("1.9.20")
  )
  override val versionMatrix = VersionMatrix(gradleList + kotlinList)

  override val kases = GradleKotlinTestVersions.from(versionMatrix)

  @TestFactory
  fun `the check lifecycle task invokes ktlintCheck`() = testFactory {

    rootBuild {
      plugins {
        kotlin("jvm", version = it.kotlinVersion)
        id("com.rickbusarow.ktlint", version = BuildConfig.version)
      }
    }

    rootSettings {

      pluginManagement {
        repositories {
          maven(stringLiteral(BuildConfig.localM2Path))
          gradlePluginPortal()
          mavenCentral()
          google()
        }
      }
      dependencyResolutionManagement {
        repositories {
          maven(stringLiteral(BuildConfig.localM2Path))
          mavenCentral()
          google()
        }
      }

      rootProjectName.setEquals("root")
    }

    workingDir.resolve("src/main/kotlin/com/test/File.kt")
      .kotlin(
        """
        package com.test

        class File


        """
      )

    shouldFail("check") {
      task(":ktlintCheckMain")?.outcome shouldBe TaskOutcome.FAILED
    }
  }
}
