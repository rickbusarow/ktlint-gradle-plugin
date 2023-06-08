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

// TODO: https://youtrack.jetbrains.com/issue/KTIJ-23114/
@file:Suppress("invisible_reference", "invisible_member")

package com.rickbusarow.ktlint

import com.rickbusarow.ktlint.internal.Ansi.Companion.noAnsi
import com.rickbusarow.ktlint.internal.remove
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldInclude
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test

internal class SourceSetTest : BaseGradleTest {

  @Test
  fun `ktlintCheck lints Gradle script files`() = test {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")

      }

      """
    }

    // creating an issue for KtLint to find
    settingsFile { settingsFile.readText().trim() }

    shouldFail("ktlintCheck") {
      task(":ktlintCheckGradleScripts")?.outcome shouldBe TaskOutcome.FAILED

      output.remove(workingDir.path).noAnsi() shouldInclude """
        > Task :ktlintCheckGradleScripts FAILED
         file:///build.gradle.kts:4:1: ❌ standard:no-blank-line-before-rbrace ╌ Unexpected blank line(s) before "}"
         file:///settings.gradle.kts:1:1: ❌ standard:final-newline ╌ File must end with a newline (\n)
      """.trimIndent()
    }
  }

  @Test
  fun `ktlintFormat fixes Gradle script files`() = test {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")

      }

      """
    }

    // creating an issue for KtLint to fix
    settingsFile { settingsFile.readText().trim() }

    shouldSucceed("ktlintFormat") {
      task(":ktlintFormatGradleScripts")?.outcome shouldBe TaskOutcome.SUCCESS

      output.remove(workingDir.path).noAnsi() shouldInclude """
        > Task :ktlintFormatGradleScripts
         file:///build.gradle.kts:4:1: ✅ standard:no-blank-line-before-rbrace ╌ Unexpected blank line(s) before "}"
         file:///settings.gradle.kts:1:1: ✅ standard:final-newline ╌ File must end with a newline (\n)
      """.trimIndent()
    }
  }
}
