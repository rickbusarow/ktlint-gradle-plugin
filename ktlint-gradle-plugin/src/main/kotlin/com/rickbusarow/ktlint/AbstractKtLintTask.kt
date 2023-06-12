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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/** @since 0.1.1 */
@CacheableTask
@Suppress("UnnecessaryAbstractClass")
abstract class AbstractKtLintTask(
  private val workerExecutor: WorkerExecutor,
  /**
   * If `true`, the task will run KtLint's "format" functionality (`--format` or `-F` in the
   * CLI). Otherwise, the task will run it in "lint" mode and fail if there are any errors.
   *
   * @since 0.1.1
   */
  @get:Input
  val autoCorrect: Boolean
) : DefaultTask() {

  init {
    group = "KtLint"
    description = "to do..."
  }

  /** @since 0.1.1 */
  @get:InputFiles
  @get:Classpath
  abstract val ktlintClasspath: ConfigurableFileCollection

  /** @since 0.1.1 */
  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val editorConfig: RegularFileProperty

  @get:Internal
  internal abstract val rootDir: DirectoryProperty

  protected fun lint(files: List<File>) {

    if (files.isEmpty()) return

    val workQueue = workerExecutor.classLoaderIsolation {
      it.classpath.setFrom(ktlintClasspath)
    }

    workQueue.submit(KtLintWorkAction::class.java) { params ->
      params.editorConfig.fileValue(editorConfig.orNull?.asFile)
      params.sourceFiles.set(files)
      params.autoCorrect.set(autoCorrect)
      params.rootDir.set(rootDir)
    }

    workQueue.await()
  }
}

/** @since 0.1.1 */
@CacheableTask
abstract class KtLintCheckTask @Inject constructor(
  workerExecutor: WorkerExecutor
) : AbstractKtLintTask(workerExecutor, autoCorrect = false) {

  init {
    group = "KtLint"
    description = "Checks Kotlin code for correctness"
  }

  /** @since 0.1.1 */
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  @TaskAction
  fun execute() {
    lint(sourceFiles.files.toList())
  }
}
