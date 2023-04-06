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

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.RELATIVE
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class KtLintSyncRuleSetJarTask : Sync() {

  @get:InputDirectory
  @get:PathSensitive(RELATIVE)
  abstract val jarFolder: DirectoryProperty

  @get:InputFile
  @get:PathSensitive(RELATIVE)
  abstract val xmlFile: RegularFileProperty

  @TaskAction
  fun execute() {

    val xml = xmlFile.get().asFile

    require(xml.isFile)

    val rootProjectDir: File = xml.parentFile.parentFile

    val oldText = xml.readText()

    val updateReg = Regex(
      """([\s\S]*<externalJarPaths>\s*<list>\s*?\R?)([^\S\r\n]*)[\s\S]*?\R(\s*<\/list>[\s\S]*)"""
    )

    val updateMatch = updateReg.find(oldText)

    if (updateMatch != null) {
      updateExistingList(updateMatch, oldText, rootProjectDir, xml)
    } else {

      createNewList(oldText, rootProjectDir, xml)
    }
  }

  private fun updateExistingList(
    updateMatch: MatchResult,
    oldText: String,
    rootProjectDir: File,
    xml: File
  ) {
    val jarDir = jarFolder.get().asFile

    val (before, indent, after) = updateMatch.destructured

    val newText = buildString(oldText.length) {
      append(before)

      jarDir.walkTopDown()
        .filter { it.isFile }
        .forEach { jarFile ->
          appendLine(
            "$indent<option value=\"\$PROJECT_DIR\$/${jarFile.relativeTo(rootProjectDir)}\" />"
          )
        }

      append(after)
    }

    if (newText != oldText) {
      xml.writeText(newText)
    }
  }

  private fun createNewList(oldText: String, rootProjectDir: File, xml: File) {
    val jarDir = jarFolder.get().asFile
    val newReg = Regex("""([\s\S]*?\R?)([^\S\r\n]*)(<\/component>[\s\S]*)""")

    val (before, indent, after) = newReg.find(oldText)?.destructured
      ?: return

    fun indent(times: Int) = indent.repeat(times)

    @Suppress("MagicNumber")
    val newText = buildString(oldText.length) {
      append(before)

      appendLine("${indent(2)}<externalJarPaths>")
      appendLine("${indent(3)}<list>")

      jarDir.walkTopDown()
        .filter { it.isFile }
        .forEach { jarFile ->
          appendLine(
            "${indent(4)}<option value=\"\$PROJECT_DIR\$/${jarFile.relativeTo(rootProjectDir)}\" />"
          )
        }

      appendLine("${indent(3)}</list>")
      appendLine("${indent(2)}</externalJarPaths>")
      append("$indent$after")
    }

    if (newText != oldText) {
      xml.writeText(newText)
    }
  }
}
