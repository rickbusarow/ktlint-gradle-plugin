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

package com.rickbusarow.ktlint.internal

import com.rickbusarow.ktlint.internal.Ansi.Companion.noAnsi
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.ir.types.IdSignatureValues.result
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

internal class KtLintResultBlockTest {

  @Test
  fun `results for the same file are sorted by position`() {

    val results = KtLintResultList(
      listOf(
        0 to 1,
        0 to 0,
        213 to 1,
        213 to -1,
        213 to 3,
        174 to 100,
        174 to Int.MIN_VALUE,
        174 to Int.MAX_VALUE,
        405 to 100,
        Int.MIN_VALUE to 100,
        Int.MAX_VALUE to 100
      ).map { (line, col) ->
        KtLintResult(
          true,
          file = File("root/src/Subject.kt"),
          line = line,
          col = col,
          detail = "detail",
          ruleId = "rule-id"
        )
      }
    )

    val expected = """
      |   file: src/Subject.kt
      |         RULE ID  DETAIL  FILE
      |      ✔  rule-id  detail  file://root/src/Subject.kt:-2147483648:100:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:0:0:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:0:1:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:174:-2147483648:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:174:100:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:174:2147483647:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:213:-1:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:213:1:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:213:3:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:405:100:
      |      ✔  rule-id  detail  file://root/src/Subject.kt:2147483647:100:
      |
    """.trimMargin()

    block(results, maxDetailWidth = 60) shouldBe expected
  }

  @Nested
  inner class `detail wrapping` {

    @Test
    fun `long detail strings are wrapped`() {

      val detail = ("a" + "a a".repeat(40)).dropLast(1)
      val result = KtLintResult(
        true,
        file = File("root/src/Subject.kt"),
        line = 1,
        col = 1,
        detail = detail,
        ruleId = "rule-id"
      )

      block(result, maxDetailWidth = 60) shouldBe """
        |   file: src/Subject.kt
        |         RULE ID  DETAIL                                                       FILE
        |      ✔  rule-id  aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa  file://root/src/Subject.kt:1:1:
        |                  aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa aa
        |
      """.trimMargin()
    }

    @Test
    fun `detail wraps only between words`() {
      val detail = "aaa ".repeat(15) + "aaa ".repeat(15)
      val result = KtLintResult(
        true,
        file = File("root/src/Subject.kt"),
        line = 1,
        col = 1,
        detail = detail,
        ruleId = "rule-id"
      )

      val expected = """
        |   file: src/Subject.kt
        |         RULE ID  DETAIL                                                       FILE
        |      ✔  rule-id  aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa  file://root/src/Subject.kt:1:1:
        |                  aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa aaa
        |
      """.trimMargin()

      block(result, maxDetailWidth = 60) shouldBe expected
    }

    @Test
    fun `a custom maxDetailWidth is used`() {
      val detail = "a ".repeat(15) + "a ".repeat(15)
      val result = KtLintResult(
        true,
        file = File("root/src/Subject.kt"),
        line = 1,
        col = 1,
        detail = detail,
        ruleId = "rule-id"
      )

      val expected = """
        |   file: src/Subject.kt
        |         RULE ID  DETAIL                         FILE
        |      ✔  rule-id  a a a a a a a a a a a a a a a  file://root/src/Subject.kt:1:1:
        |                  a a a a a a a a a a a a a a a
        |
      """.trimMargin()

      block(result, maxDetailWidth = 30) shouldBe expected
    }

    @Test
    fun `detail wrapping is greedy`() {
      val detail = "a ".repeat(15) + "a ".repeat(4)
      val result = KtLintResult(
        true,
        file = File("root/src/Subject.kt"),
        line = 1,
        col = 1,
        detail = detail,
        ruleId = "rule-id"
      )

      val expected = """
        |   file: src/Subject.kt
        |         RULE ID  DETAIL                         FILE
        |      ✔  rule-id  a a a a a a a a a a a a a a a  file://root/src/Subject.kt:1:1:
        |                  a a a a
        |
      """.trimMargin()

      block(result, maxDetailWidth = 30) shouldBe expected
    }

    @Test
    fun `detail does not wrap if less than maxDetailWidth`() {
      val detail = "a".repeat(29)
      val result = KtLintResult(
        true,
        file = File("root/src/Subject.kt"),
        line = 1,
        col = 1,
        detail = detail,
        ruleId = "rule-id"
      )

      val expected = """
        |   file: src/Subject.kt
        |         RULE ID  DETAIL                         FILE
        |      ✔  rule-id  aaaaaaaaaaaaaaaaaaaaaaaaaaaaa  file://root/src/Subject.kt:1:1:
        |
      """.trimMargin()

      block(result, maxDetailWidth = 30) shouldBe expected
    }

    @Test
    fun `detail does not wrap if exactly maxDetailWidth`() {
      val detail = "a".repeat(30)
      val result = KtLintResult(
        true,
        file = File("root/src/Subject.kt"),
        line = 1,
        col = 1,
        detail = detail,
        ruleId = "rule-id"
      )

      val expected = """
        |   file: src/Subject.kt
        |         RULE ID  DETAIL                          FILE
        |      ✔  rule-id  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa  file://root/src/Subject.kt:1:1:
        |
      """.trimMargin()

      block(result, maxDetailWidth = 60) shouldBe expected
    }

    @Test
    fun `detail does not wrap a single word if it is longer than maxDetailWidth`() {
      val detail = "aaa " + "a".repeat(31)
      val result = KtLintResult(
        true,
        file = File("root/src/Subject.kt"),
        line = 1,
        col = 1,
        detail = detail,
        ruleId = "rule-id"
      )

      val expected = """
        |   file: src/Subject.kt
        |         RULE ID  DETAIL                           FILE
        |      ✔  rule-id  aaa                              file://root/src/Subject.kt:1:1:
        |                  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |
      """.trimMargin()

      block(result, maxDetailWidth = 30) shouldBe expected
    }
  }

  private fun block(result: KtLintResult, maxDetailWidth: Int, root: File = File("root")): String {
    return KtLintResultList(result)
      .block(root = root, maxDetailWidth = maxDetailWidth)
      .also(::println)
      .noAnsi()
      .replace(File.separator, "/")
  }

  private fun block(
    results: List<KtLintResult>,
    maxDetailWidth: Int,
    root: File = File("root")
  ): String {
    return KtLintResultList(results)
      .block(root = root, maxDetailWidth = maxDetailWidth)
      .also(::println)
      .noAnsi()
      .replace(File.separator, "/")
  }
}
