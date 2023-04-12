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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

abstract class KtlintTask(
  private val workerExecutor: WorkerExecutor,
  private val autoCorrect: Boolean
) : DefaultTask() {

  @get:InputFiles
  @get:Classpath
  abstract val ktlintClasspath: ConfigurableFileCollection

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val editorConfig: RegularFileProperty

  @get:Incremental
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  /**
   * The directory where the updated documentation files will be written during the execution of this
   * task.
   *
   * This property serves as a workaround for the limitations of incremental tasks, which can't have
   * the same inputs as their outputs. Since this task is a formatting task that modifies input files,
   * we can't use the actual input files as outputs without risking that they will be deleted by Gradle
   * in case of a binary change to the plugin or build environment. Instead, we use this property to
   * declare a separate directory as the output of this task.
   *
   * Any time this task writes changes to an input file, it also creates a stub file with the same
   * relative path inside the sourceFilesShadow directory. During the next incremental build, the task
   * will only need to update the real input files that have changed since the last build, and the
   * contents of the sourceFilesShadow directory will be ignored.
   *
   * Note that the contents of the sourceFilesShadow directory are not meant to be used by other tasks
   * or processes, and should not be relied on as a source of truth. Its sole purpose is to allow this
   * task to run incrementally without interfering with other tasks that might need to use the same
   * files.
   */
  @get:OutputDirectory
  internal abstract val sourceFilesShadow: DirectoryProperty

  @TaskAction
  fun execute(inputChanges: InputChanges) {

    val workQueue = workerExecutor.classLoaderIsolation {
      it.classpath.setFrom(ktlintClasspath)
    }

    val extensions = setOf("kt", "kts")

    val fileChanges = inputChanges.getFileChanges(sourceFiles)
      .mapNotNull { fileChange ->
        fileChange.file
          .takeIf { it.isFile && it.extension in extensions }
      }

    workQueue.submit(KtLintWorker::class.java) { params ->
      params.editorConfig.fileValue(editorConfig.get().asFile)
      params.sourceFiles.set(fileChanges)
      params.autoCorrect.set(autoCorrect)

      params.sourceFilesShadow.set(sourceFilesShadow)
    }
  }

  interface KtLintWorkParameters : WorkParameters {

    val autoCorrect: Property<Boolean>
    val sourceFiles: ListProperty<File>
    val editorConfig: RegularFileProperty

    val sourceFilesShadow: DirectoryProperty
  }

  abstract class KtLintWorker : WorkAction<KtLintWorkParameters> {
    override fun execute() {

      val engine = KtLintEngineWrapper(
        editorConfigPath = parameters.editorConfig.get().asFile,
        autoCorrect = parameters.autoCorrect.get()
      )

      val shadow = parameters.sourceFilesShadow.get().asFile

      val formatResults = engine.execute(parameters.sourceFiles.get())
        .filterIsInstance<KtLintEngineWrapper.KtLintFormatResult>()

      for (result in formatResults) {

        val file = result.kotlinFile

        val relative = file.relativeTo(shadow)
          .normalize()
          .path
          .removePrefix(shadow.path)
          .split(File.separator)
          .dropWhile { it == ".." && it.isNotBlank() }
          .joinToString(File.separator)
          .replace(file.extension, "txt")

        shadow.resolve(relative)
          .createSafely(result.outContent.hashCode().toString())
      }
    }
  }
}

abstract class KtlintFormatTask @Inject constructor(
  workerExecutor: WorkerExecutor
) : KtlintTask(workerExecutor, autoCorrect = true) {
  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Checks Kotlin code for correctness and fixes what it can"
  }
}

abstract class KtlintCheckTask @Inject constructor(
  workerExecutor: WorkerExecutor
) : KtlintTask(workerExecutor, autoCorrect = false) {

  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Checks Kotlin code for correctness"
  }

  @get:OutputFile
  abstract val htmlReportFile: RegularFileProperty
}
