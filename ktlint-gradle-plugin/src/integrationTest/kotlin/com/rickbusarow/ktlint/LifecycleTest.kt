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

import com.rickbusarow.kase.TestEnvironmentFactory
import com.rickbusarow.kase.asTests
import com.rickbusarow.kase.gradle.DependencyVersion.Gradle
import com.rickbusarow.kase.gradle.DependencyVersion.Kotlin
import com.rickbusarow.kase.gradle.VersionMatrix
import com.rickbusarow.kase.gradle.kases
import com.rickbusarow.ktlint.internal.div
import io.kotest.matchers.collections.shouldContainInOrder
import org.gradle.api.internal.provider.ValueSupplier.ValueProducer.task
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

@Suppress("RemoveEmptyClassBody")
internal class LifecycleTest : BaseGradleTest, TestEnvironmentFactory<TestEnvironment> {

  val gradleList = listOf(Gradle("8.3"), Gradle("8.4"))
  val kotlinList = listOf(
    Kotlin("1.8.11"),
    Kotlin("1.8.22"),
    Kotlin("1.9.0"),
    Kotlin("1.9.10")
  )
  val matrix = VersionMatrix(gradleList + kotlinList)
  val kases = matrix.kases(Gradle, Kotlin)

  @TestFactory
  fun `the check lifecycle task invokes ktlintCheck`() = kases.asTests {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")
      }
      """
    }

    workingDir.resolve("src/main/kotlin/com/test/File.kt")
      .kotlin(
        """
        package com.test

        class File

        """
      )

    shouldFail("check") {
      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.FAILED
    }
  }

  @Test
  fun `ktlintCheck and ktlintFormat exist even if the project does not have a Kotlin plugin`() =
    test {

      buildFile {
        """
        plugins {
          id("com.rickbusarow.ktlint")
        }
        """
      }

      shouldSucceed("ktlintCheck", "ktlintFormat") {
        task(":ktlintFormat")?.outcome shouldBe TaskOutcome.SUCCESS
        task(":ktlintCheck")?.outcome shouldBe TaskOutcome.SUCCESS
      }
    }

  @Test
  fun `ktlintFormat is cacheable`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
      }

      """
    }

    val testKitDir = workingDir / "testKit"

    shouldSucceed("ktlintFormat", "--build-cache", withHermeticTestKit = true) {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    // TODO <Rick> delete me
    run {
      println()
      println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
      println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
      println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
      println()
    }

    workingDir.resolve("build").deleteRecursively()

    shouldSucceed(
      "ktlintFormat",
      "--build-cache",
      "-i",
      withHermeticTestKit = true
    ) {

      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.FROM_CACHE
    }
  }

  @Test
  fun `ktlintFormat is is up to date immediately after a successful format`() = test {

    buildFile {
      """
      plugins {
        id ("com.rickbusarow.ktlint")
      }

      """
    }

    shouldSucceed("ktlintFormat", "--build-cache") {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    shouldSucceed("ktlintFormat", "--build-cache") {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.UP_TO_DATE
    }
  }

  @Test
  fun `gradle scripts tasks exist even if the project does not have a Kotlin plugin`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
      }
      """
    }

    shouldSucceed("ktlintCheckGradleScripts", "ktlintFormatGradleScripts") {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
    }
  }

  @Test
  fun `the fix lifecycle task invokes ktlintFormat`() = test {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")
      }

      val fix by tasks.registering
      """
    }

    workingDir
      .resolve("src/main/kotlin/com/test/File.kt")
      .kotlin(
        """
        package com.test

        class File { }

        """
      )

    shouldSucceed("fix") {
      task(":ktlintFormat")?.outcome shouldBe TaskOutcome.SUCCESS
    }
  }

  @Test
  fun `the ktlintCheck must run after ktlintFormat if both are invoked`() = test {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")
      }

      val fix by tasks.registering
      """
    }

    workingDir
      .resolve("src/main/kotlin/com/test/File.kt")
      .kotlin(
        """
        package com.test

        class File { }

        """
      )

    shouldSucceed("ktlintCheck", "ktlintFormat") {
      tasks.map { it.path } shouldContainInOrder listOf(":ktlintFormat", ":ktlintCheck")
      task(":ktlintFormat")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintCheck")?.outcome shouldBe TaskOutcome.SUCCESS
    }
  }
}
