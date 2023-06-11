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

import com.rickbusarow.ktlint.internal.createSafely
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType.FILE
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType.REMOVED
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor

/** */
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintFormatIntermediateTask(
  private val workerExecutor: WorkerExecutor
) : DefaultTask() {

  init {
    group = "KtLint"
    description =
      "creates stubs of the original source files in order to make the format task incremental"
  }

  /**  */
  @get:Incremental
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  /**
   * The directory where the intermediate files will be written during the execution of this task.
   */
  @get:OutputDirectory
  internal abstract val intermediateDirectory: DirectoryProperty

  @get:Internal
  internal abstract val projectRootDirectory: DirectoryProperty

  @TaskAction
  fun execute(inputChanges: InputChanges) {

    val extensions = setOf("kt", "kts")

    val projectRoot = projectRootDirectory.get().asFile
    val intermediateRoot = intermediateDirectory.get().asFile

    inputChanges.getFileChanges(sourceFiles)
      .asSequence()
      .filter { it.fileType == FILE }
      .filter { it.file.extension in extensions }
      .forEach { change ->

        val relativePathKt = change.file.toRelativeString(projectRoot)
        val relativePathTxt = relativePathKt.replaceAfterLast('.', "txt")

        val textFile = intermediateRoot.resolve(relativePathTxt)

        textFile.delete()

        if (change.changeType != REMOVED) {
          textFile.createSafely(relativePathKt)
        }
      }
  }
}
