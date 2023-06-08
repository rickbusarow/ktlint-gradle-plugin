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

package com.rickbusarow.ktlint.testing

import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

internal object TestPsiFileFactory {

  private val PSI_PROJECT = KotlinCoreEnvironment.createForProduction(
    parentDisposable = Disposer.newDisposable(),
    configuration = CompilerConfiguration(),
    configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
  ).project

  private val KT_FILE_FACTORY = KtPsiFactory(PSI_PROJECT, markGenerated = false)

  internal fun createKotlin(
    name: String,
    @Language("kotlin")
    content: String
  ): KtFile = KT_FILE_FACTORY.createFile(name, content)
}
