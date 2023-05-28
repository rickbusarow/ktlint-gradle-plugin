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

import com.rickbusarow.ktlint.KtLintEngineWrapper.ReportedResult.Companion.block
import com.rickbusarow.ktlint.internal.createSafely
import com.rickbusarow.ktlint.internal.suffixIfNot
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.io.File

@Suppress("RemoveEmptyClassBody")
internal class KtLintEngineWrapperTest {

  @Test
  fun `canary test`() = test {

    kotlin(
      "Subject.kt",
      """
      package com.test

      class Subject { }
      """.trimIndent()
    )

    format()
      .block()
      // TODO <Rick> delete me
      .also(::println)
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
          .suffixIfNot("\n\n")
      )

    fun editorconfig(
      path: String = ".editorconfig",
      @Language("editorconfig") content: String
    ): File = workingDir.resolve(path)
      .createSafely(content.trimIndent().suffixIfNot("\n\n"))

    fun format(
      editorConfigPath: File? = workingDir.resolve(".editorconfig"),
      files: List<File> = workingDir.walkBottomUp()
        .filter { it.isFile && it.extension in setOf("kt", "kts") }
        .toList()
    ) = KtLintEngineWrapper(editorConfigPath, true).execute(files)
  }
}
