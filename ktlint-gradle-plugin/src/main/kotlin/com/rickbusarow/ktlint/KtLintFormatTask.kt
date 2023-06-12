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

import com.rickbusarow.ktlint.internal.GradleProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

/** @since 0.1.1 */
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintFormatTask @Inject constructor(
  workerExecutor: WorkerExecutor
) : AbstractKtLintTask(workerExecutor, autoCorrect = true) {
  init {
    group = "KtLint"
    description = "Checks Kotlin code for correctness and fixes what it can"
  }

  /**  */
  @get:Incremental
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val intermediateFiles: ConfigurableFileCollection

  /** @since 0.1.1 */
  // @get:OutputFiles
  // abstract val sourceFiles: ConfigurableFileCollection
  @get:OutputDirectories
  abstract val sourceFiles: GradleProperty<FileCollection>

  @TaskAction
  fun execute(inputChanges: InputChanges) {

    val root = rootDir.get().asFile

    val fileChanges = inputChanges.getFileChanges(intermediateFiles)
      .filter { it.file.isFile }
      .map { fileChange ->

        root.resolve(fileChange.file.readLines().first())
      }
      .mapNotNull { file ->

        if (!file.exists()) {
          println("file doesn't exist: $file")
        }

        file.takeIf { file.isFile }
      }

    lint(fileChanges)
  }
}
