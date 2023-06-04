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

  private val wrappedDetails by lazy(NONE) { mutableMapOf<Int, String>() }
  internal fun detailWrapped(maxDetailWidth: Int): String {
    return wrappedDetails.getOrPut(maxDetailWidth) {
      detail.wrap(maxDetailWidth)
    }
  }

  private fun String.wrap(maxWidth: Int): String {
    if (length <= maxWidth) return this@wrap
    if (isBlank()) return this@wrap

    val words = split(Regex("""\s+""")).filter { it.isNotBlank() }

    val wrapped = StringBuilder()

    var currentLine = StringBuilder(words.first())

    for (word in words.drop(1)) {
      val currentWordLength = word.length
      val currentLineLength = currentLine.length

      when {
        currentLineLength + 1 + currentWordLength <= maxWidth -> {
          // The word fits on the current line with a space before it.
          currentLine.append(" ")
          currentLine.append(word)
        }

        else -> {
          // The word does not fit on the current line, so start a new line.
          wrapped.append(currentLine)
          wrapped.appendLine()
          currentLine = StringBuilder(word)
        }
      }
    }

    // Add the last line to the wrapped text.
    wrapped.append(currentLine)

    return wrapped.toString()
  }

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

  val hasFailures: Boolean by lazy(NONE) { results.any { !it.fixed } }

  fun isNotEmpty(): Boolean = results.isNotEmpty()

  fun block(root: File, maxDetailWidth: Int): String {

    return groupBy { it.file }
      .entries
      .sortedBy { it.key }
      .joinToString("\n") { (file, group) ->

        val ruleWidth = group.maxOf { it.ruleId.length } + 2
        val detailWidth = group
          .flatMap { it.detailWrapped(maxDetailWidth).lineSequence() }
          .maxOf { it.length }
          .plus(2)

        buildString {

          append(" ".repeat(INDENT / 2))
          append("file: ${file.relativeTo(root)}".ansi(Ansi.Color.BRIGHT_YELLOW))
          appendLine()

          append(" ".repeat(INDENT))
          append(" ".repeat(FIX_ICON_LENGTH))
          append("RULE ID".ansi(Ansi.Style.UNDERLINE, Ansi.Style.BOLD, padEnd = ruleWidth))
          append("DETAIL".ansi(Ansi.Style.UNDERLINE, Ansi.Style.BOLD, padEnd = detailWidth))
          append("FILE".ansi(Ansi.Style.UNDERLINE, Ansi.Style.BOLD))
          appendLine()

          group.sorted()
            .forEach {
              append(
                it.reportLine(
                  indent = INDENT,
                  ruleWidth = ruleWidth,
                  detailWidth = detailWidth,
                  maxDetailWidth = maxDetailWidth
                )
              )
            }
        }
      }
  }

  private fun KtLintResult.reportLine(
    indent: Int,
    ruleWidth: Int,
    detailWidth: Int,
    maxDetailWidth: Int
  ): String {

    val icon = if (fixed) {
      "✔".ansi(Ansi.Color.BRIGHT_GREEN, padEnd = FIX_ICON_LENGTH)
    } else {
      "X".ansi(Ansi.Color.BRIGHT_RED, padEnd = FIX_ICON_LENGTH)
    }

    val ruleColor = if (fixed) {
      Ansi.Color.BRIGHT_GREEN
    } else {
      Ansi.Color.BRIGHT_RED
    }

    val wrappedDetailLines = detailWrapped(maxDetailWidth).lines()

    return buildString {
      append(" ".repeat(indent))
      append(icon.padEnd(FIX_ICON_LENGTH))
      append(ruleId.ansi(ruleColor, padEnd = ruleWidth))

      append(wrappedDetailLines[0].padEnd(detailWidth))
      append(fileWithPosition)
      appendLine()

      for (line in wrappedDetailLines.drop(1)) {
        append(" ".repeat(indent))
        append(" ".repeat(FIX_ICON_LENGTH))
        append(" ".repeat(ruleWidth))
        append(line)
        appendLine()
      }
    }
  }

  companion object {
    private const val INDENT = 6
    private const val FIX_ICON_LENGTH = 3
  }
}
