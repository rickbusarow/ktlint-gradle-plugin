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

import com.pinterest.ktlint.core.KtLintRuleEngine
import com.pinterest.ktlint.core.LintError
import com.pinterest.ktlint.core.RuleSetProviderV2
import com.pinterest.ktlint.core.api.EditorConfigDefaults
import com.rickbusarow.ktlint.internal.resolveInParentOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs a KtLint format against generated files, using editorconfig settings located at
 * [editorConfigPath] if that file exists.
 */
class KtLintEngineWrapper(
  private var editorConfigPath: File? = null,
) : java.io.Serializable {

  private val ruleProviders by lazy {
    ServiceLoader.load(RuleSetProviderV2::class.java)
      .flatMapTo(mutableSetOf()) { it.getRuleProviders() }
  }

  private val ecDefaults = ConcurrentHashMap<File, EditorConfigDefaults>()

  /** formats all listed kotlin files */
  fun execute(kotlinFiles: List<File>): List<KtLintResult> = runBlocking {

    kotlinFiles
      .map { kotlinFile ->

        val ecDefaults = getEditorConfigDefaults(kotlinFile)

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

        formatFile(kotlinFile, engine)
      }
      .toList()
      .awaitAll()
      .also { results ->

        val errors = results.mapNotNull { it.lintErrorOrNull }

        check(errors.isEmpty()) {
          "Ktlint format finished with ${errors.size} errors which were not fixed.  Check log for details."
        }
      }
  }

  private fun getEditorConfigDefaults(kotlinFile: File): EditorConfigDefaults? {

    val pathArg = editorConfigPath

    fun resolve() = kotlinFile.resolveInParentOrNull(".editorconfig")

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

    return ecDefaults.computeIfAbsent(ecPath) { EditorConfigDefaults.load(ecPath.toPath()) }
  }

  private fun CoroutineScope.formatFile(
    kotlinFile: File,
    engine: KtLintRuleEngine
  ): Deferred<KtLintResult> = async(Dispatchers.Default) {

    val inContent = kotlinFile.readText()

    var lintErrorOrNull: LintError? = null

    val outContent = engine.format(kotlinFile.toPath()) { lintError, fixed ->

      val maybeFixed: String

      if (fixed) {
        maybeFixed = " FIXED"
      } else {
        maybeFixed = ""
        lintErrorOrNull = lintError
      }

      println(
        buildString {
          append("file://$kotlinFile:${lintError.line}:${lintError.col}$maybeFixed ")
          append("${lintError.detail} [${lintError.ruleId}]")
        }
      )
    }

    if (outContent != inContent) {
      kotlinFile.writeText(outContent)
    }

    KtLintResult(
      outContent = outContent,
      kotlinFile = kotlinFile,
      lintErrorOrNull = lintErrorOrNull
    )
  }

  data class KtLintResult(
    val outContent: String,
    val kotlinFile: File,
    val lintErrorOrNull: LintError?
  ) : java.io.Serializable
}
