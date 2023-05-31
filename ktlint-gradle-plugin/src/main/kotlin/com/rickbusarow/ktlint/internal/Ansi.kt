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

@Suppress("MagicNumber")
internal sealed interface Ansi {
  val code: Int

  companion object {
    private const val ESCAPE = '\u001B'

    private val supportsAnsi = !System.getProperty("os.name")
      .contains(
        other = "win",
        ignoreCase = true
      )

    fun String.noAnsi(): String = "$ESCAPE\\[[;\\d]*m".toRegex().replace(this, "")

    fun String.ansi(vararg codes: Ansi, padStart: Int? = null, padEnd: Int? = null): String {

      fun Int?.plusAnsiLength(): Int = requireNotNull(this).letIf(supportsAnsi) {
        var totalLength = this
        codes.forEach { code ->
          totalLength += when {
            code.code < 10 -> 4
            code.code >= 100 -> 6
            else -> 5
          }
        }
        totalLength + 4 // Include reset code
      }

      return letIf(supportsAnsi) {
        codes.joinToString("", postfix = "$this$ESCAPE[0m") { "$ESCAPE[${it.code}m" }
      }
        .letIf(padStart != null) { padStart(padStart.plusAnsiLength()) }
        .letIf(padEnd != null) { padEnd(padEnd.plusAnsiLength()) }
    }
  }

  enum class Style(override val code: Int) : Ansi {
    RESET(0),
    BOLD(1),
    ITALIC(3),
    UNDERLINE(4),
    BLINK_SLOW(5),
    BLINK_FAST(6),
    REVERSE(7),
    CONCEAL(8),
    STRIKETHROUGH(9)
  }

  enum class Color(override val code: Int) : Ansi {
    BLACK(30),
    RED(31),
    GREEN(32),
    YELLOW(33),
    BLUE(34),
    MAGENTA(35),
    CYAN(36),
    WHITE(37),
    GREY(90),
    BRIGHT_RED(91),
    BRIGHT_GREEN(92),
    BRIGHT_YELLOW(93),
    BRIGHT_BLUE(94),
    BRIGHT_MAGENTA(95),
    BRIGHT_CYAN(96),
    BRIGHT_WHITE(97)
  }

  enum class BackgroundColor(override val code: Int) : Ansi {
    BLACK(40),
    RED(41),
    GREEN(42),
    YELLOW(43),
    BLUE(44),
    MAGENTA(45),
    CYAN(46),
    WHITE(47),
    BRIGHT_BLACK(100),
    BRIGHT_RED(101),
    BRIGHT_GREEN(102),
    BRIGHT_YELLOW(103),
    BRIGHT_BLUE(104),
    BRIGHT_MAGENTA(105),
    BRIGHT_CYAN(106),
    BRIGHT_WHITE(107)
  }
}
