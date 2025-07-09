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

package com.rickbusarow.ktlint

import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.ktlint.testing.KtlintGradleTest
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import io.kotest.matchers.string.shouldNotInclude
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.jupiter.api.TestFactory

internal class LifecycleTest : KtlintGradleTest() {

  @TestFactory
  fun `tasks are compatible with configuration caching`() = testFactory {
    rootProject {
      buildFileAsFile.appendText("\n")
      settingsFileAsFile.appendText("\n")
      kotlinFile(
        "src/main/kotlin/com/test/File.kt",
        """
        package com.test

        class File

        """.trimIndent()
      )
    }

    fun calculatingMessage(task: String) =
      "Calculating task graph as no configuration cache is available for tasks: $task"

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

  @TestFactory fun `the check lifecycle task invokes ktlintCheck`() = testFactory {

    rootProject {
      kotlinFile(
        "src/main/kotlin/com/test/File.kt",
        """
        package com.test

        class File

        """
      )
    }

    shouldFail("check") {
      task(":ktlintCheckGradleScripts")?.outcome shouldBe FAILED
    }
  }

  @TestFactory
  fun `ktlintCheck and ktlintFormat exist even if the project does not have a Kotlin plugin`() =
    testFactory {
      rootProject {
        buildFile {
          plugins {
            id("com.rickbusarow.ktlint")
          }
        }
      }

      shouldSucceed("ktlintCheck", "ktlintFormat") {
        task(":ktlintFormat")?.outcome shouldBe SUCCESS
        task(":ktlintCheck")?.outcome shouldBe SUCCESS
      }
    }

  @TestFactory
  fun `ktlintFormat is cacheable`() = testFactory {

    rootProject {
      buildFileAsFile.appendText("\n")
      settingsFileAsFile.appendText("\n")
    }

    shouldSucceed("ktlintFormat", "--build-cache", withHermeticTestKit = true) {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe SUCCESS
      // This is already cached since it's just an empty umbrella task.
      task(":ktlintFormat")?.outcome shouldBe FROM_CACHE
    }

    workingDir.resolve("build").deleteRecursively()

    shouldSucceed("ktlintFormat", "--build-cache", withHermeticTestKit = true) {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe FROM_CACHE
      task(":ktlintFormat")?.outcome shouldBe FROM_CACHE
    }
  }

  @TestFactory
  fun `gradle scripts tasks exist even if the project does not have a Kotlin plugin`() =
    testFactory {

      rootProject.buildFile {
        plugins {
          id("com.rickbusarow.ktlint")
        }
      }

      shouldSucceed("ktlintCheckGradleScripts", "ktlintFormatGradleScripts") {
        task(":ktlintFormatGradleScripts")?.outcome shouldBe SUCCESS
        task(":ktlintCheckGradleScripts")?.outcome shouldBe SUCCESS
      }
    }

  @TestFactory fun `the fix lifecycle task invokes ktlintFormat`() = testFactory {

    rootProject {
      buildFile {
        plugins {
          kotlin("jvm")
          id("com.rickbusarow.ktlint")
        }

        raw("val fix by tasks.registering")
      }

      kotlinFile(
        "src/main/kotlin/com/test/File.kt",
        """
        package com.test

        class File { }

        """
      )
    }

    shouldSucceed("fix") {
      task(":ktlintFormat")?.outcome shouldBe SUCCESS
    }
  }

  @TestFactory
  fun `the ktlintCheck must run after ktlintFormat if both are invoked`() = testFactory {

    rootProject {
      buildFile {
        plugins {
          kotlin("jvm")
          id("com.rickbusarow.ktlint")
        }

        raw("val fix by tasks.registering")
      }

      kotlinFile(
        "src/main/kotlin/com/test/File.kt",
        """
          package com.test

          class File { }

        """.trimIndent()
      )
    }

    shouldSucceed("ktlintCheck", "ktlintFormat") {
      tasks.map { it.path } shouldContainInOrder listOf(":ktlintFormat", ":ktlintCheck")
      task(":ktlintFormat")?.outcome shouldBe SUCCESS
      task(":ktlintCheck")?.outcome shouldBe SUCCESS
    }
  }
}
