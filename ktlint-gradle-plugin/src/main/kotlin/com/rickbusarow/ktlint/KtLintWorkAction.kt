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

import com.rickbusarow.ktlint.internal.GradleLogger
import com.rickbusarow.ktlint.internal.GradleLogging
import com.rickbusarow.ktlint.internal.GradleProperty
import com.rickbusarow.ktlint.internal.existsOrNull
import com.rickbusarow.ktlint.internal.md5
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

/** @since 0.1.1 */
abstract class KtLintWorkAction : WorkAction<KtLintWorkAction.KtLintWorkParameters> {
  override fun execute() {

    val logger: GradleLogger = GradleLogging.getLogger("ktlint logger Gradle")

    val engine = KtLintEngineWrapper(
      editorConfigPath = parameters.editorConfig.orNull?.asFile,
      autoCorrect = parameters.autoCorrect.get()
    )

    val results = engine.execute(parameters.sourceFiles.get())

    if (parameters.autoCorrect.get()) {

      val projectDir = parameters.projectRoot.get().asFile

      val mapFile = parameters.outputMap.get().asFile

      val oldMap = mapFile.existsOrNull()?.readMap().orEmpty()

      val newMap = buildMap {

        putAll(oldMap.filter { (relative, _) -> projectDir.resolve(relative).exists() })

        for (result in results) {
          val file = result.file

          val relative = file.toRelativeString(projectDir)

          put(relative, file.md5())
        }

        val changed = results.filter { it.fixed }
          .map { it.file.toRelativeString(projectDir) }

        put("changed-files", changed.joinToString(","))
      }

      mapFile.writeMap(newMap)
    }

    if (results.isNotEmpty()) {
      logger.lifecycle(
        results.block()
      )

      val errors = results.filter { !it.fixed }

      if (errors.isNotEmpty()) {

        throw GradleException(
          "Ktlint format finished with ${errors.size} errors which were not fixed.  " +
            "Check log for details."
        )
      }
    }
  }

  /** @since 0.1.1 */
  interface KtLintWorkParameters : WorkParameters {
    /** @since 0.1.1 */
    val autoCorrect: GradleProperty<Boolean>

    /** @since 0.1.1 */
    val sourceFiles: ListProperty<File>

    /** @since 0.1.1 */
    val editorConfig: RegularFileProperty

    /** @since 0.1.1 */
    val outputMap: RegularFileProperty

    /** @since 0.1.1 */
    val projectRoot: DirectoryProperty
  }
}
