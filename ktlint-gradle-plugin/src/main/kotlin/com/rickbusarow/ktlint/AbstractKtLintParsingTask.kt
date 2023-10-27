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

import com.rickbusarow.ktlint.internal.KtLintOutputHashes.Companion.readFormatOutput
import com.rickbusarow.ktlint.internal.existsOrNull
import com.rickbusarow.ktlint.internal.md5
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/** @since 0.1.1 */
@CacheableTask
@Suppress("UnnecessaryAbstractClass")
abstract class AbstractKtLintParsingTask(
  private val layout: ProjectLayout,
  private val workerExecutor: WorkerExecutor,
  /**
   * If `true`, the task will run KtLint's "format" functionality (`--format` or `-F` in the
   * CLI). Otherwise, the task will run it in "lint" mode and fail if there are any errors.
   *
   * @since 0.1.1
   */
  @get:Input
  val autoCorrect: Boolean
) : AbstractKtLintTask() {

  /** @since 0.1.1 */
  @get:InputFiles
  @get:CompileClasspath
  abstract val ktlintClasspath: ConfigurableFileCollection

  /** @since 0.1.1 */
  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val editorConfig: RegularFileProperty

  @TaskAction
  fun execute(inputChanges: InputChanges) {

    val extensions = setOf("kt", "kts")

    val projectDir = layout.projectDirectory.asFile

    val lastChanged = outputHashFile.asFile.get()
      .existsOrNull()
      ?.readFormatOutput()
      ?.let { formatOutput ->

        formatOutput.changedFilesRelative
          .filter { relative ->
            projectDir.resolve(relative).md5() != formatOutput[relative]
          }
      }
      .orEmpty()

    val fileChanges = inputChanges.getFileChanges(sourceFiles)
      .mapNotNull { fileChange ->
        fileChange.file.takeIf { it.isFile && it.extension in extensions }
      }
      .plus(lastChanged)

    val workQueue = workerExecutor.classLoaderIsolation {
      it.classpath.setFrom(ktlintClasspath)
    }

    workQueue.submit(KtLintWorkAction::class.java) { params ->
      params.editorConfig.fileValue(editorConfig.orNull?.asFile)
      params.sourceFiles.set(fileChanges)
      params.autoCorrect.set(autoCorrect)
      params.projectRoot.set(projectDir)

      params.formatOutputFile.set(outputHashFile)
    }

    workQueue.await()
  }

  internal fun changedFilesAreUnchanged(): Boolean {

    val formatOutput = outputHashFile.asFile.get()
      .existsOrNull()
      ?.readFormatOutput()
      ?: return false

    val projectDir = layout.projectDirectory.asFile

    val currentFiles = sourceFiles.files

    if (currentFiles.isEmpty()) return true

    return currentFiles.all { file ->
      val relative = file.relativeTo(projectDir)

      file.md5() == formatOutput[relative]
    }
  }
}

/** @since 0.1.1 */
@CacheableTask
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintFormatTask @Inject constructor(
  layout: ProjectLayout,
  workerExecutor: WorkerExecutor
) : AbstractKtLintParsingTask(layout, workerExecutor, autoCorrect = true) {
  init {
    description = "Checks Kotlin code for correctness and fixes what it can"
  }
}

/** @since 0.1.1 */
@CacheableTask
abstract class KtLintCheckTask @Inject constructor(
  layout: ProjectLayout,
  workerExecutor: WorkerExecutor
) : AbstractKtLintParsingTask(layout, workerExecutor, autoCorrect = false) {

  init {
    description = "Checks Kotlin code for correctness"
  }
}
