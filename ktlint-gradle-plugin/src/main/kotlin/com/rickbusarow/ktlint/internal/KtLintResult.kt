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

import com.rickbusarow.ktlint.internal.Ansi.Companion.ansi
import java.io.File
import kotlin.LazyThreadSafetyMode.NONE

internal data class KtLintResult(
  val fixed: Boolean,
  val file: File,
  val line: Int,
  val col: Int,
  val detail: String,
  val ruleId: String
) : Comparable<KtLintResult> {

  val fileWithPosition by lazy(NONE) { "file://$file:$line:$col:" }

  override fun compareTo(other: KtLintResult): Int {
    return compareValuesBy(
      this,
      other,
      { it.fixed },
      { it.file },
      { it.line },
      { it.col },
      { it.ruleId },
      { it.detail }
    )
  }
}

internal data class KtLintResultList(
  private val results: List<KtLintResult>
) : List<KtLintResult> by results {
  constructor(vararg results: KtLintResult) : this(results.toList())

  fun isNotEmpty(): Boolean = results.isNotEmpty()

  fun block(): String {

    return groupBy { it.file }
      .entries
      .sortedBy { it.key }
      .joinToString("\n") { (_, group) ->

        group.sorted()
          .joinToString("\n") {
            it.reportLine()
          }
      }
  }

  private fun KtLintResult.reportLine(): String {

    val symbol = if (fixed) '✅' else '❌'

    val ruleColor = if (fixed) {
      Ansi.Color.BRIGHT_GREEN
    } else {
      Ansi.Color.BRIGHT_RED
    }

    return buildString {

      append(" ")
      append(fileWithPosition)
      append(" ")

      append(symbol)
      append(" ")

      append(ruleId.ansi(ruleColor))
      append(" ╌ ")

      append(detail.trim())
    }
  }
}
