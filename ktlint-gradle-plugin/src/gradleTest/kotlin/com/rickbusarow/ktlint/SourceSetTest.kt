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

// TODO: https://youtrack.jetbrains.com/issue/KTIJ-23114/
@file:Suppress("invisible_reference", "invisible_member")

package com.rickbusarow.ktlint

import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.kase.stdlib.remove
import com.rickbusarow.ktlint.testing.KtlintGradleTest
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlin.konan.file.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

@Suppress("RemoveEmptyClassBody")
internal class SourceSetTest : KtlintGradleTest() {

  @TestFactory
  fun `script tasks have explicit inputs for only script files`() = testFactory {

    rootProject {
      buildFileAsFile {
        """
        plugins {
          id("com.rickbusarow.ktlint")
        }

        val badTask by tasks.registering(SourceTask::class) {
          source(buildFile)
          outputs.file(projectDir.resolve("api/api.txt"))
          onlyIf { true }
        }
        """.trimIndent()
      }

      // creating an issue for KtLint to find
      settingsFileAsFile { settingsFileAsFile.readText().trim() }
    }

    shouldSucceed(
      "ktlintCheckGradleScripts",
      "ktlintFormatGradleScripts",
      "badTask",
      "--rerun-tasks"
    ) {

      outputCleaned shouldInclude """
        build.gradle.kts:9:1 ✅ standard:final-newline ═ File must end with a newline (\n)
        settings.gradle.kts:21:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @TestFactory
  fun `script tasks ignore script files in a build directory`() = testFactory {

    rootProject {

      kotlinFile(
        "build/generated/my-plugin.gradle.kts",
        """
        package com.test

        class MyPlugin { }

        """.trimIndent()
      )
    }

    shouldSucceed("ktlintCheckGradleScripts", "ktlintFormatGradleScripts") {

      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      outputCleaned shouldInclude """
        build.gradle.kts:7:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @TestFactory
  fun `script tasks ignore script files in an included build directory`() = testFactory {

    rootProject {

      dir("build-logic") {
        file(
          relativePath = "build.gradle.kts",
          content = this@rootProject.buildFileAsFile.readText()
        )
        file(
          relativePath = "settings.gradle.kts",
          content = this@rootProject.settingsFileAsFile.readText()
            .lineSequence()
            .drop(1)
            .joinToString("\n")
        )
      }

      settingsFileAsFile {
        settingsFileAsFile.readText()
          .replace(
            "pluginManagement {",
            "pluginManagement {\n  includeBuild(\"build-logic\")"
          )
      }
    }

    shouldSucceed("ktlintCheckGradleScripts", "ktlintFormatGradleScripts") {

      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      outputCleaned shouldInclude """
        build.gradle.kts:7:1 ✅ standard:final-newline ═ File must end with a newline (\n)
        settings.gradle.kts:22:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }

    // ensure that tasks against the build-logic directory would find more stuff
    shouldSucceed(
      "-p",
      "build-logic",
      "ktlintCheckGradleScripts",
      "ktlintFormatGradleScripts"
    ) {

      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      outputCleaned shouldInclude """
        build-logic/build.gradle.kts:7:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @TestFactory
  fun `script tasks ignore script files in a sub project directory`() = testFactory {

    rootProject {
      project("lib") {
        buildFileAsFile { this@rootProject.buildFileAsFile.readText() }
      }

      settingsFileAsFile.appendText("\ninclude(\"lib\")")
    }

    shouldSucceed(":ktlintCheckGradleScripts", ":ktlintFormatGradleScripts") {

      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      outputCleaned shouldInclude """
        build.gradle.kts:7:1 ✅ standard:final-newline ═ File must end with a newline (\n)
        settings.gradle.kts:22:14 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }

    // ensure that tasks against the lib would find more stuff
    shouldSucceed(
      ":lib:ktlintCheckGradleScripts",
      ":lib:ktlintFormatGradleScripts"
    ) {

      task(":lib:ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":lib:ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      outputCleaned shouldInclude """
        lib/build.gradle.kts:7:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @TestFactory
  fun `ktlintCheck lints Gradle script files`() = testFactory {

    rootProject {
      buildFileAsFile {
        """
        plugins {
          id("com.rickbusarow.ktlint")

        }

        """.trimIndent()
      }

      // creating an issue for KtLint to find
      settingsFileAsFile { settingsFileAsFile.readText().trim() }
    }

    shouldFail("ktlintCheck") {
      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.FAILED

      outputCleaned shouldInclude """
        build.gradle.kts:3:1 ❌ standard:no-blank-line-before-rbrace ═ Unexpected blank line(s) before "}"
        settings.gradle.kts:21:1 ❌ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @TestFactory
  fun `ktlintFormat fixes Gradle script files`() = testFactory {

    rootProject {
      buildFileAsFile {
        """
        plugins {
          id("com.rickbusarow.ktlint")

        }

        """.trimIndent()
      }

      // creating an issue for KtLint to fix
      settingsFileAsFile { settingsFileAsFile.readText().trim() }
    }

    shouldSucceed("ktlintFormat") {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      outputCleaned shouldInclude """
        build.gradle.kts:3:1 ✅ standard:no-blank-line-before-rbrace ═ Unexpected blank line(s) before "}"
        settings.gradle.kts:21:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @TestFactory
  fun `source set tasks are registered if the Kotlin plugin is applied after ktlint`() =
    testFactory {

      rootProject {

        buildFile {
          plugins {
            id("com.rickbusarow.ktlint")
            kotlin("jvm")
          }
        }
        kotlinFile(
          "src/main/kotlin/com/test/File.kt",
          """
            package com.test

            class File { }

          """.trimIndent()
        )
      }

      shouldSucceed("ktlintFormat") {
        task(":ktlintFormatMain")?.outcome shouldBe TaskOutcome.SUCCESS

        outputCleaned shouldInclude """
        src/main/kotlin/com/test/File.kt:3:12 ✅ standard:no-empty-class-body ═ Unnecessary block ("{}")
        """.trimIndent()
      }
    }

  @TestFactory
  fun `generated files are not checked`() = testFactory {

    rootProject {
      buildFile(
        """
        plugins {
          id("com.rickbusarow.ktlint")
          kotlin("jvm")
          `kotlin-dsl`
        }

        dependencies {
          compileOnly(gradleApi())
        }

        """.trimIndent()
      )

      settingsFileAsFile.appendText("\n")

      // This will make the `kotlin-dsl` plugin generate a file.
      kotlinFile(
        "src/main/kotlin/convention.gradle.kts",
        """
        fun foo() { }

        """.trimIndent()
      )
    }

    // first, generate the code with kotlin-dsl
    shouldSucceed("assemble")

    val generatedFile = workingDir
      .resolve("build/generated-sources/kotlin-dsl-plugins/kotlin")
      .resolve("ConventionPlugin.kt")

    generatedFile.shouldExist()

    // If the plugin were to check the generated code, it would happen here.
    shouldSucceed("ktlintCheck")

    // Manually check the generated convention plugin and confirm that KtLint throws errors for it.
    val engineWrapper = KtLintEngineWrapper(editorConfigPath = null, autoCorrect = false)
    engineWrapper.execute(listOf(generatedFile)).shouldNotBeEmpty()
  }
}
