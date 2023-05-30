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
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.propertyTypes
import com.rickbusarow.ktlint.internal.KtLintResult
import com.rickbusarow.ktlint.internal.KtLintResultList
import com.rickbusarow.ktlint.internal.commonParent
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
  fun execute(kotlinFiles: List<File>): KtLintResultList = runBlocking {

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
      .let { KtLintResultList(it) }
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
  ): Deferred<MutableList<KtLintResult>> = async(Dispatchers.Default) {

    val results = mutableListOf<KtLintResult>()

    engine.lint(Code.fromFile(kotlinFile)) { lintError ->

      results.add(
        KtLintResult(
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
  ): Deferred<MutableList<KtLintResult>> = async(Dispatchers.Default) {

    val inContent by lazy(NONE) { kotlinFile.readText() }

    val results = mutableListOf<KtLintResult>()

    val outContent = engine.format(Code.fromFile(kotlinFile)) { lintError, fixed ->

      results.add(
        KtLintResult(
          fixed = fixed,
          file = kotlinFile,
          line = lintError.line,
          col = lintError.col,
          detail = lintError.detail,
          ruleId = lintError.ruleId.value
        )
      )
    }

    if (results.isNotEmpty() && outContent != inContent) {
      kotlinFile.writeText(outContent)
    }
    results
  }
}
