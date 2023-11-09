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

import com.rickbusarow.ktlint.internal.KtLintInputHashes
import com.rickbusarow.ktlint.internal.md5
import com.rickbusarow.ktlint.internal.writeSerializable
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import javax.inject.Inject

@Deprecated(
  "renamed to com.rickbusarow.ktlint.AbstractKtLintTask",
  ReplaceWith(
    expression = "AbstractKtLintTask",
    "com.rickbusarow.ktlint.AbstractKtLintTask"
  )
)
typealias KtLintTask = AbstractKtLintTask

/** */
@CacheableTask
@Suppress("UnnecessaryAbstractClass")
abstract class AbstractKtLintTask : DefaultTask() {

  init {
    group = KtLintPlugin.TASK_GROUP
  }

  /** */
  @get:Incremental
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  /**
   * This property serves as a workaround for the limitations of incremental tasks, which
   * can't have the same inputs as their outputs. Since this is a formatting plugin that
   * modifies input files, we can't use the actual input files as outputs without risking
   * that they will be deleted by Gradle in case of a change to the build environment.
   * Instead, we use this property to declare a separate directory as the output of this task.
   *
   * Any time the format task writes changes to an input file, it also creates a stub file with
   * the same relative path inside the sourceFilesShadow directory. During the next incremental
   * build, the task will only need to update the real input files that have changed since
   * the last build, and the contents of the sourceFilesShadow directory will be ignored.
   *
   * Note that the contents of the sourceFilesShadow directory are not meant
   * to be used by other tasks or processes, and should not be relied on as a
   * source of truth. Its sole purpose is to allow this task to run incrementally
   * without interfering with other tasks that might need to use the same files.
   *
   * @since 0.1.1
   */
  @get:OutputFile
  internal abstract val outputHashFile: RegularFileProperty
}

/** */
@CacheableTask
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintHashingTask @Inject constructor(
  private val layout: ProjectLayout
) : AbstractKtLintTask() {
  init {
    description = "Checks Kotlin code for correctness and fixes what it can"
  }

  @TaskAction
  fun execute(inputChanges: InputChanges) {

    val projectDir = layout.projectDirectory.asFile

    val hashMap = sourceFiles.files
      .associate { file ->
        file.relativeTo(projectDir) to file.md5()
      }

    outputHashFile.asFile.get()
      .writeSerializable(KtLintInputHashes(hashMap))
  }
}
