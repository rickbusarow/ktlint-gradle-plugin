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

import com.pinterest.ktlint.cli.ruleset.core.api.RuleSetProviderV3
import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigDefaults
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.api.LintError
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.propertyTypes
import com.rickbusarow.ktlint.KtLintEngineWrapper.Color.Companion.colorized
import com.rickbusarow.ktlint.KtLintEngineWrapper.Color.Companion.supported
import com.rickbusarow.ktlint.KtLintEngineWrapper.Color.LIGHT_GREEN
import com.rickbusarow.ktlint.KtLintEngineWrapper.Color.LIGHT_RED
import com.rickbusarow.ktlint.KtLintEngineWrapper.KtLintResult.LintErrorWithFixed
import com.rickbusarow.ktlint.KtLintEngineWrapper.Style.Companion.style
import com.rickbusarow.ktlint.KtLintEngineWrapper.Style.UNDERLINE
import com.rickbusarow.ktlint.internal.commonParent
import com.rickbusarow.ktlint.internal.resolveInParentOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.gradle.api.GradleException
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Runs a KtLint format against generated files, using editorconfig
 * settings located at [editorConfigPath] if that file exists.
 */
internal class KtLintEngineWrapper(
  private val editorConfigPath: File?,
  private val autoCorrect: Boolean,
  ruleProviders: Lazy<Set<RuleProvider>> = lazy {
    ServiceLoader.load(RuleSetProviderV3::class.java)
      .flatMapTo(mutableSetOf()) { it.getRuleProviders() }
  }
) : java.io.Serializable {

  private val ruleProviders by ruleProviders

  private val ecDefaults = ConcurrentHashMap<File, EditorConfigDefaults>()

  /** formats all listed kotlin files */
  fun execute(kotlinFiles: List<File>): List<ReportedResult> = runBlocking {

    val ecDefaults = getEditorConfigDefaults(kotlinFiles)

    val engine = if (ecDefaults != null) {
      KtLintRuleEngine(
        ruleProviders = ruleProviders,
        editorConfigDefaults = ecDefaults,
        isInvokedFromCli = false
      )
    } else {
      KtLintRuleEngine(
        ruleProviders = ruleProviders,
        isInvokedFromCli = false
      )
    }
    kotlinFiles.map { kotlinFile ->

      if (autoCorrect) {
        formatFile(kotlinFile, engine)
      } else {
        lintFile(kotlinFile, engine)
      }
    }
      .awaitAll()
      .flatten()
      .also { results ->

        val errors = results.filter { !it.fixed }

        if (errors.isNotEmpty()) {
          throw GradleException(
            "Ktlint format finished with ${errors.size} errors which were not fixed.  " +
              "Check log for details."
          )
        }
      }
  }

  private fun getEditorConfigDefaults(kotlinFiles: List<File>): EditorConfigDefaults? {

    val pathArg = editorConfigPath

    fun resolve() = kotlinFiles
      .takeIf { it.isNotEmpty() }
      ?.commonParent()
      ?.resolveInParentOrNull(".editorconfig")

    val ecPath = if (pathArg != null) {
      if (!pathArg.exists()) {
        println("Could not find an .editorconfig file at path: $pathArg")
        resolve()
      } else {
        pathArg
      }
    } else {
      resolve()
    }
      ?: return null

    return ecDefaults.computeIfAbsent(ecPath) {
      EditorConfigDefaults.load(
        path = ecPath.toPath(),
        propertyTypes = ruleProviders.propertyTypes()
      )
    }
  }

  private fun CoroutineScope.lintFile(
    kotlinFile: File,
    engine: KtLintRuleEngine
  ): Deferred<MutableList<ReportedResult>> = async(Dispatchers.Default) {

    val results = mutableListOf<ReportedResult>()

    engine.lint(Code.fromFile(kotlinFile)) { lintError ->

      results.add(
        ReportedResult(
          fixed = false,
          file = kotlinFile,
          line = lintError.line,
          col = lintError.col,
          detail = lintError.detail,
          ruleId = lintError.ruleId.value
        )
      )
    }

    results
  }

  private fun CoroutineScope.formatFile(
    kotlinFile: File,
    engine: KtLintRuleEngine
  ): Deferred<MutableList<ReportedResult>> = async(Dispatchers.Default) {

    val inContent by lazy(NONE) { kotlinFile.readText() }

    val lintErrors = mutableListOf<LintErrorWithFixed>()
    val results = mutableListOf<ReportedResult>()

    val outContent = engine.format(Code.fromFile(kotlinFile)) { lintError, fixed ->

      val maybeFixed = if (fixed) " FIXED".colorized(LIGHT_GREEN) else ""

      lintErrors.add(LintErrorWithFixed(fixed, lintError))

      results.add(
        ReportedResult(
          fixed = fixed,
          file = kotlinFile,
          line = lintError.line,
          col = lintError.col,
          detail = lintError.detail,
          ruleId = lintError.ruleId.value
        )
      )
    }

    if (lintErrors.isNotEmpty() && outContent != inContent) {
      kotlinFile.writeText(outContent)
    }
    results
  }

  internal sealed interface KtLintResult : java.io.Serializable {
    val kotlinFile: File
    val lintErrors: List<LintErrorWithFixed>

    data class LintErrorWithFixed(
      val fixed: Boolean,
      val lintError: LintError
    )
  }

  internal data class KtLintFormatResult(
    val outContent: String,
    override val kotlinFile: File,
    override val lintErrors: List<LintErrorWithFixed>
  ) : KtLintResult

  internal data class KtLintCheckResult(
    override val kotlinFile: File,
    override val lintErrors: List<LintErrorWithFixed>
  ) : KtLintResult

  data class ReportedResult(
    val fixed: Boolean,
    val file: File,
    val line: Int,
    val col: Int,
    val detail: String,
    val ruleId: String
  ) : Comparable<ReportedResult> {

    val link = "file://$file:$line:$col"

    override fun compareTo(other: ReportedResult): Int {
      return compareValuesBy(
        this,
        other,
        { it.fixed },
        { it.link },
        { it.ruleId },
        { it.detail }
      )
    }

    companion object {

      fun List<ReportedResult>.block(): String {

        fun ReportedResult.reportLine(
          indent: Int,
          ruleWidth: Int,
          detailWidth: Int
        ): String {

          val icon = if (fixed) "✔".colorized(LIGHT_GREEN) else "X".colorized(LIGHT_RED)

          return buildString {
            append(" ".repeat(indent))
            append("$icon  ")
            append(ruleId.padEnd(ruleWidth))
            append(detail.padEnd(detailWidth))
            appendLine(link)
          }
        }

        val indent = 6
        val ruleWidth = maxOf { it.ruleId.length } + 2
        val detailWidth = maxOf { it.detail.length } + 2

        return buildString {

          append(" ".repeat(indent + 3))
          append("rule id".style(UNDERLINE).padEnd(ruleWidth + 8))
          append("detail".style(UNDERLINE).padEnd(detailWidth + 8))
          appendLine("file".style(UNDERLINE))

          this@block.sorted()
            .forEach {
              append(
                it.reportLine(
                  indent = indent,
                  ruleWidth = ruleWidth,
                  detailWidth = detailWidth
                )
              )
            }
        }
      }
    }
  }

  enum class Style(val code: Int) {
    RESET(0),
    BOLD(1),
    ITALIC(3),
    UNDERLINE(4);

    companion object {

      fun String.style(style: Style): String {
        return if (supported) {
          "\u001B[${style.code}m$this\u001B[0m"
        } else {
          this
        }
      }
    }
  }

  @Suppress("MagicNumber")
  internal enum class Color(val code: Int) {
    LIGHT_RED(91),
    LIGHT_YELLOW(93),
    LIGHT_BLUE(94),
    LIGHT_GREEN(92),
    LIGHT_MAGENTA(95),
    RED(31),
    YELLOW(33),
    BLUE(34),
    GREEN(32),
    MAGENTA(35),
    CYAN(36),
    LIGHT_CYAN(96),
    ORANGE_DARK(38),
    ORANGE_BRIGHT(48),
    PURPLE_DARK(53),
    PURPLE_BRIGHT(93),
    PINK_BRIGHT(198),
    BROWN_DARK(94),
    BROWN_BRIGHT(178),
    LIGHT_GRAY(37),
    DARK_GRAY(90),
    BLACK(30),
    WHITE(97);

    companion object {

      internal val supported = "win" !in System.getProperty("os.name").lowercase()

      fun String.noColors(): String = "\u001B\\[[;\\d]*m".toRegex().replace(this, "")

      /** returns a string in the given color */
      fun String.colorized(color: Color): String {

        return if (supported) {
          "\u001B[${color.code}m$this\u001B[0m"
        } else {
          this
        }
      }
    }
  }
}
