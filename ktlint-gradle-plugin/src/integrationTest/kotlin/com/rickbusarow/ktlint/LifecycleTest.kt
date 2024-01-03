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

package com.rickbusarow.ktlint

import com.rickbusarow.ktlint.BuildConfig.gradleVersion
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.string.shouldNotInclude
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.Test

@Suppress("RemoveEmptyClassBody")
internal class LifecycleTest : BaseGradleTest {

  @Test
  fun `tasks are compatible with configuration caching`() = test {
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

    fun calculatingMessage(task: String) = when {
      gradleVersion >= "8.5" -> "Calculating task graph as no cached configuration is available for tasks: $task"
      else -> "Calculating task graph as no configuration cache is available for tasks: $task"
    }

    val storedMessage = "Configuration cache entry stored."
    val reusedMessage = "Configuration cache entry reused."

    shouldSucceed("ktlintCheck", "--configuration-cache") {
      output shouldInclude calculatingMessage("ktlintCheck")
      output shouldInclude storedMessage
      output shouldNotInclude reusedMessage
    }
    shouldSucceed("ktlintCheck", "--configuration-cache") {
      output shouldInclude "Reusing configuration cache."
      output shouldNotInclude storedMessage
      output shouldInclude reusedMessage
    }

    shouldSucceed("ktlintFormat", "--configuration-cache") {
      output shouldInclude calculatingMessage("ktlintFormat")
      output shouldInclude storedMessage
      output shouldNotInclude reusedMessage
    }
    shouldSucceed("ktlintFormat", "--configuration-cache") {
      output shouldInclude "Reusing configuration cache."
      output shouldNotInclude storedMessage
      output shouldInclude reusedMessage
    }
  }

  @Test
  fun `the check lifecycle task invokes ktlintCheck`() = test {

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
      task(":ktlintCheckGradleScripts")?.outcome shouldBe FAILED
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
        task(":ktlintFormat")?.outcome shouldBe SUCCESS
        task(":ktlintCheck")?.outcome shouldBe SUCCESS
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

    shouldSucceed("ktlintFormat", "--build-cache", withHermeticTestKit = true) {
      task(":ktlintFormat")?.outcome shouldBe SUCCESS
    }

    workingDir.resolve("build").deleteRecursively()

    shouldSucceed("ktlintFormat", "--build-cache", withHermeticTestKit = true) {
      task(":ktlintFormat")?.outcome shouldBe FROM_CACHE
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
      task(":ktlintFormatGradleScripts")?.outcome shouldBe SUCCESS
      task(":ktlintCheckGradleScripts")?.outcome shouldBe SUCCESS
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
      task(":ktlintFormat")?.outcome shouldBe SUCCESS
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
      task(":ktlintFormat")?.outcome shouldBe SUCCESS
      task(":ktlintCheck")?.outcome shouldBe SUCCESS
    }
  }
}
