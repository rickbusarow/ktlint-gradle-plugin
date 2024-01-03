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

// TODO: https://youtrack.jetbrains.com/issue/KTIJ-23114/
@file:Suppress("invisible_reference", "invisible_member")

package com.rickbusarow.ktlint

import com.rickbusarow.ktlint.internal.Ansi.Companion.noAnsi
import com.rickbusarow.ktlint.internal.createSafely
import com.rickbusarow.ktlint.internal.remove
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

internal class SourceSetTest : BaseGradleTest {

  @Test
  fun `script tasks have explicit inputs for only script files`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
      }

      val badTask by tasks.registering(SourceTask::class) {
        source(buildFile)
        outputs.file(projectDir.resolve("api/api.txt"))
        onlyIf { true }
      }
      """
    }

    // creating an issue for KtLint to find
    settingsFile { settingsFile.readText().trim() }

    shouldSucceed(
      "ktlintCheckGradleScripts",
      "ktlintFormatGradleScripts",
      "badTask",
      "--rerun-tasks"
    ) {

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///build.gradle.kts:9:1 ✅ standard:final-newline ═ File must end with a newline (\n)
        file:///settings.gradle.kts:23:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @Test
  fun `script tasks ignore script files in a build directory`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
      }
      """
    }

    @Suppress("RemoveEmptyClassBody")
    workingDir
      .resolve("build/generated/my-plugin.gradle.kts")
      .kotlin(
        """
        package com.test

        class MyPlugin { }

        """
      )

    shouldSucceed("ktlintCheckGradleScripts", "ktlintFormatGradleScripts") {

      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///build.gradle.kts:3:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @Test
  fun `script tasks ignore script files in an included build directory`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
      }
      """
    }

    with(workingDir.resolve("build-logic")) {
      resolve("build.gradle.kts")
        .createSafely(buildFile.readText())
      resolve("settings.gradle.kts")
        .createSafely(settingsFile.readText().lineSequence().drop(1).joinToString("\n"))
    }

    settingsFile {
      settingsFile.readText()
        .replace(
          "pluginManagement {",
          "pluginManagement {\n  includeBuild(\"build-logic\")"
        )
    }

    shouldSucceed("ktlintCheckGradleScripts", "ktlintFormatGradleScripts") {

      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///build.gradle.kts:3:1 ✅ standard:final-newline ═ File must end with a newline (\n)
        file:///settings.gradle.kts:24:1 ✅ standard:final-newline ═ File must end with a newline (\n)
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

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///build-logic/build.gradle.kts:3:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @Test
  fun `script tasks ignore script files in a sub project directory`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
      }
      """
    }

    with(workingDir.resolve("lib")) {
      resolve("build.gradle.kts")
        .createSafely(buildFile.readText())
    }

    settingsFile {
      settingsFile.readText()
        .plus("include(\"lib\")")
    }

    shouldSucceed(":ktlintCheckGradleScripts", ":ktlintFormatGradleScripts") {

      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///build.gradle.kts:3:1 ✅ standard:final-newline ═ File must end with a newline (\n)
        file:///settings.gradle.kts:24:14 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }

    // ensure that tasks against the lib would find more stuff
    shouldSucceed(
      ":lib:ktlintCheckGradleScripts",
      ":lib:ktlintFormatGradleScripts"
    ) {

      task(":lib:ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS
      task(":lib:ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///lib/build.gradle.kts:3:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @Test
  fun `ktlintCheck lints Gradle script files`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")

      }

      """
    }

    // creating an issue for KtLint to find
    settingsFile { settingsFile.readText().trim() }

    shouldFail("ktlintCheck") {
      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.FAILED

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///build.gradle.kts:3:1 ❌ standard:no-blank-line-before-rbrace ═ Unexpected blank line(s) before "}"
        file:///settings.gradle.kts:23:1 ❌ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @Test
  fun `ktlintFormat fixes Gradle script files`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")

      }

      """
    }

    // creating an issue for KtLint to fix
    settingsFile { settingsFile.readText().trim() }

    shouldSucceed("ktlintFormat") {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///build.gradle.kts:3:1 ✅ standard:no-blank-line-before-rbrace ═ Unexpected blank line(s) before "}"
        file:///settings.gradle.kts:23:1 ✅ standard:final-newline ═ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @Test
  fun `source set tasks are registered if the Kotlin plugin is applied after ktlint`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
        kotlin("jvm")
      }
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

    shouldSucceed("ktlintFormat") {
      task(":ktlintFormatMain")?.outcome shouldBe TaskOutcome.SUCCESS

      output.remove(workingDir.path).noAnsi() shouldInclude """
        file:///src/main/kotlin/com/test/File.kt:3:12 ✅ standard:no-empty-class-body ═ Unnecessary block ("{}")
      """.trimIndent()
    }
  }

  @Test
  fun `generated files are not checked`() = test {

    buildFile {
      """
      plugins {
        id("com.rickbusarow.ktlint")
        kotlin("jvm")
        `kotlin-dsl`
      }

      dependencies {
        compileOnly(gradleApi())
      }

      """
    }

    workingDir
      .resolve("src/main/kotlin/convention.gradle.kts")
      .kotlin(
        """
        fun foo() { }

        """
      )

    // first, generate the code with kotlin-dsl
    shouldSucceed("assemble")

    // If the plugin were to check the generated code, it would happen here.
    shouldSucceed("ktlintCheck")

    val generatedFile = workingDir
      .resolve("build/generated-sources/kotlin-dsl-plugins/kotlin")
      .resolve("ConventionPlugin.kt")

    val engineWrapper = KtLintEngineWrapper(editorConfigPath = null, autoCorrect = false)

    // Manually check the generated convention plugin and confirm that KtLint throws errors for it.
    engineWrapper.execute(listOf(generatedFile)).shouldNotBeEmpty()
  }
}
