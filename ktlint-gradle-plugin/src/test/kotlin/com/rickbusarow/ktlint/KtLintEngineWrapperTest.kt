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

import com.rickbusarow.ktlint.internal.KtLintResult
import com.rickbusarow.ktlint.internal.KtLintResultList
import com.rickbusarow.ktlint.internal.createSafely
import com.rickbusarow.ktlint.internal.existsOrNull
import com.rickbusarow.ktlint.internal.suffixIfNot
import io.kotest.matchers.shouldBe
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.io.File

@Suppress("RemoveEmptyClassBody")
internal class KtLintEngineWrapperTest {

  @Test
  fun `check fails for a fixable error`() = test {

    kotlin(
      "Subject.kt",
      """
      package com.test

      class Subject { }
      """.trimIndent()
    )

    check() shouldBe listOf(
      KtLintResult(
        fixed = false,
        file = workingDir.resolve("Subject.kt"),
        line = 3,
        col = 15,
        detail = """Unnecessary block ("{}")""",
        ruleId = "standard:no-empty-class-body"
      )
    )
  }

  @Test
  fun `format fixes a fixable error`() = test {

    kotlin(
      "Subject.kt",
      """
      package com.test

      class Subject { }
      """.trimIndent()
    )

    format() shouldBe listOf(
      KtLintResult(
        fixed = true,
        file = workingDir.resolve("Subject.kt"),
        line = 3,
        col = 15,
        detail = """Unnecessary block ("{}")""",
        ruleId = "standard:no-empty-class-body"
      )
    )
  }

  inline fun test(action: TestEnvironment.() -> Unit) {
    TestEnvironment().action()
  }

  class TestEnvironment {

    val workingDir: File by lazy { kotlin.io.path.createTempDirectory().toFile() }

    fun kotlin(
      path: String,
      @Language("kotlin") content: String
    ): File = workingDir.resolve(path)
      .createSafely(
        content.trimIndent()
          .suffixIfNot("\n")
      )

    fun editorconfig(
      path: String = ".editorconfig",
      @Language("editorconfig") content: String
    ): File = workingDir.resolve(path)
      .createSafely(content.trimIndent().suffixIfNot("\n\n"))

    fun check(
      editorConfigPath: File? = workingDir.resolve(".editorconfig").existsOrNull(),
      files: List<File> = workingDir.walkBottomUp()
        .filter { it.isFile && it.extension in setOf("kt", "kts") }
        .toList()
    ): KtLintResultList {
      return KtLintEngineWrapper(editorConfigPath, autoCorrect = false).execute(files)
        .also { println(it.block(root = workingDir, maxDetailWidth = 60)) }
    }

    fun format(
      editorConfigPath: File? = workingDir.resolve(".editorconfig").existsOrNull(),
      files: List<File> = workingDir.walkBottomUp()
        .filter { it.isFile && it.extension in setOf("kt", "kts") }
        .toList()
    ): KtLintResultList {
      return KtLintEngineWrapper(editorConfigPath, autoCorrect = true).execute(files)
        .also { println(it.block(root = workingDir, maxDetailWidth = 60)) }
    }
  }
}
