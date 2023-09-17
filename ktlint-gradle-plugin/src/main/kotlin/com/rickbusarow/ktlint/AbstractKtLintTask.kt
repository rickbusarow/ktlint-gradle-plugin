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

import com.rickbusarow.ktlint.internal.existsOrNull
import com.rickbusarow.ktlint.internal.md5
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkerExecutor
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import javax.inject.Inject

/** @since 0.1.1 */
@CacheableTask
@Suppress("UnnecessaryAbstractClass")
abstract class AbstractKtLintTask(
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
) : DefaultTask() {

  init {
    group = "KtLint"
    description = "to do..."
  }

  /** @since 0.1.1 */
  @get:InputFiles
  @get:CompileClasspath
  abstract val ktlintClasspath: ConfigurableFileCollection

  /** @since 0.1.1 */
  @get:Optional
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val editorConfig: RegularFileProperty

  /**   */
  @get:Incremental
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val sourceFiles: ConfigurableFileCollection

  /**
   * The directory where the updated documentation files
   * will be written during the execution of this task.
   *
   * This property serves as a workaround for the limitations of incremental tasks, which can't
   * have the same inputs as their outputs. Since this task is a formatting task that modifies
   * input files, we can't use the actual input files as outputs without risking that they
   * will be deleted by Gradle in case of a binary change to the plugin or build environment.
   * Instead, we use this property to declare a separate directory as the output of this task.
   *
   * Any time this task writes changes to an input file, it also creates a stub file with the
   * same relative path inside the sourceFilesShadow directory. During the next incremental
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
  internal abstract val outputMap: RegularFileProperty

  @TaskAction
  fun execute(inputChanges: InputChanges) {

    val extensions = setOf("kt", "kts")

    val projectDir = layout.projectDirectory.asFile

    val lastChanged = outputMap.asFile.get()
      .existsOrNull()?.readMap()
      ?.let { map ->

        map["changed-files"]
          ?.split("""\s*,\s*""".toRegex())
          ?.filter { it.isNotBlank() }
          ?.map { layout.projectDirectory.file(it).asFile }
          .orEmpty()
          .filter { it.md5() != map[it.toRelativeString(projectDir)] }
      }
      .orEmpty()

    val fileChanges = inputChanges.getFileChanges(sourceFiles)
      .mapNotNull { fileChange ->
        fileChange.file.takeIf { it.isFile && it.extension in extensions }
      }
      .plus(lastChanged)

    if (fileChanges.isEmpty()) return

    val workQueue = workerExecutor.classLoaderIsolation {
      it.classpath.setFrom(ktlintClasspath)
    }

    workQueue.submit(KtLintWorkAction::class.java) { params ->
      params.editorConfig.fileValue(editorConfig.orNull?.asFile)
      params.sourceFiles.set(fileChanges)
      params.autoCorrect.set(autoCorrect)
      params.projectRoot.set(projectDir)

      params.outputMap.set(outputMap)
    }

    workQueue.await()
  }

  internal fun changedFilesAreUnchanged(): Boolean {
    val map = outputMap.asFile.get().existsOrNull()?.readMap()
      ?: return true

    val projectDir = layout.projectDirectory.asFile

    return map["changed-files"]
      ?.takeIf { it.isNotBlank() }
      ?.split(',')
      ?.map { projectDir.resolve(it) }
      .orEmpty()
      .all { it.md5() == map[it.toRelativeString(projectDir)] }
  }
}

internal fun File.readMap(): Map<String, String> {
  val mapAsObject = ObjectInputStream(
    ByteArrayInputStream(
      FileInputStream(this@readMap).use { fis -> fis.readBytes() }
    )
  )
    .readObject()

  @Suppress("UNCHECKED_CAST")
  return mapAsObject as Map<String, String>
}

internal fun File.writeMap(newMap: Map<String, String>) {

  val newMapBytes = ByteArrayOutputStream().use { byteStream ->
    ObjectOutputStream(byteStream).use { objectStream ->
      objectStream.writeObject(newMap)
    }
    byteStream.toByteArray()
  }

  parentFile.mkdirs()

  writeBytes(newMapBytes)
}

/** @since 0.1.1 */
@CacheableTask
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintFormatTask @Inject constructor(
  layout: ProjectLayout,
  workerExecutor: WorkerExecutor
) : AbstractKtLintTask(layout, workerExecutor, autoCorrect = true) {
  init {
    group = "KtLint"
    description = "Checks Kotlin code for correctness and fixes what it can"
  }
}

/** @since 0.1.1 */
@CacheableTask
abstract class KtLintCheckTask @Inject constructor(
  layout: ProjectLayout,
  workerExecutor: WorkerExecutor
) : AbstractKtLintTask(layout, workerExecutor, autoCorrect = false) {

  init {
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Checks Kotlin code for correctness"
  }
}