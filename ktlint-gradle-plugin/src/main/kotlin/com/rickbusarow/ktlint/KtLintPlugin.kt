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

import com.rickbusarow.ktlint.internal.addAsDependencyTo
import com.rickbusarow.ktlint.internal.capitalize
import com.rickbusarow.ktlint.internal.dependsOn
import com.rickbusarow.ktlint.internal.isRootProject
import com.rickbusarow.ktlint.internal.matchingName
import com.rickbusarow.ktlint.internal.registerOnce
import com.rickbusarow.ktlint.internal.resolveInParentOrNull
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import java.io.File

/** */
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintPlugin : Plugin<Project> {
  override fun apply(target: Project) {

    val configProvider: NamedDomainObjectProvider<Configuration> = target.configurations
      .register("ktlint")

    BuildConfig.deps.split(",")
      .map { it.trim() }
      .forEach { coords ->
        target.dependencies.add("ktlint", coords)
      }

    val editorConfigFile = lazy {
      target.projectDir.resolveInParentOrNull(".editorconfig")
    }

    target.plugins.withId("org.jetbrains.kotlin.jvm") {
      registerKotlinTasks(target, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.android") {
      registerKotlinTasks(target, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.js") {
      registerKotlinTasks(target, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
      registerKotlinTasks(target, editorConfigFile, configProvider)
    }

    if (target.isRootProject()) {
      target.registerSyncRuleSetJars(configProvider)
    }
  }

  private fun registerKotlinTasks(
    target: Project,
    editorConfigFile: Lazy<File?>,
    configProvider: NamedDomainObjectProvider<Configuration>?
  ) {

    val kotlinExtension = try {
      val extension = target.extensions.findByName("kotlin") ?: return

      extension as? org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer ?: return
    } catch (_: ClassNotFoundException) {
      return
    } catch (_: NoClassDefFoundError) {
      return
    }

    val sourceSetNames = kotlinExtension.sourceSets.names

    val pairs = sourceSetNames.map { sourceSetName ->
      registerFormatCheckPair(
        target = target,
        taskNameSuffix = sourceSetName.capitalize(),
        sourceFileShadowDirectory = target.sourceFileShadowDirectory(sourceSetName),
        sourceDirectorySet = kotlinExtension.sourceSets.named(sourceSetName).map { it.kotlin },
        editorConfigFile = editorConfigFile,
        configProvider = configProvider
      )
    }

    val formatTasks = pairs.map { it.first }
    val lintTasks = pairs.map { it.second }

    var rootName = "root"
    while (rootName in sourceSetNames) {
      rootName += "_"
    }

    val (format, check) = registerFormatCheckPair(
      target = target,
      taskNameSuffix = "",
      sourceFileShadowDirectory = target.sourceFileShadowDirectory(rootName),
      sourceDirectorySet = null,
      editorConfigFile = editorConfigFile,
      configProvider = configProvider
    )

    format.dependsOn(formatTasks)
    check.dependsOn(lintTasks)
  }

  private fun registerFormatCheckPair(
    target: Project,
    taskNameSuffix: String,
    sourceFileShadowDirectory: Provider<Directory>,
    sourceDirectorySet: Provider<FileCollection>?,
    editorConfigFile: Lazy<File?>,
    configProvider: NamedDomainObjectProvider<Configuration>?
  ): Pair<TaskProvider<KtlintFormatTask>, TaskProvider<KtlintCheckTask>> {

    val formatTaskName = "ktlintFormat${taskNameSuffix.capitalize()}"

    val formatTask =
      target.tasks.registerOnce(formatTaskName, KtlintFormatTask::class.java) { task ->

        task.sourceFiles.from(sourceDirectorySet)

        task.ktlintClasspath.setFrom(configProvider)

        task.sourceFilesShadow.set(sourceFileShadowDirectory)
        task.editorConfig.fileValue(editorConfigFile.value)
      }
        .addAsDependencyTo(target.tasks.matchingName("fix"))

    val checkTaskName = "ktlintCheck${taskNameSuffix.capitalize()}"

    val lintTask = target.tasks.registerOnce(checkTaskName, KtlintCheckTask::class.java) { task ->

      task.sourceFiles.from(sourceDirectorySet)

      task.ktlintClasspath.setFrom(configProvider)

      // If both tasks are running in the same invocation, make sure that the format task runs first.
      task.mustRunAfter(formatTask)

      task.sourceFilesShadow.set(sourceFileShadowDirectory)
      task.editorConfig.fileValue(editorConfigFile.value)
    }
      .addAsDependencyTo(target.tasks.matchingName("check"))

    return formatTask to lintTask
  }

  private fun Project.registerSyncRuleSetJars(
    configProvider: NamedDomainObjectProvider<Configuration>
  ) {
    tasks.register("syncRuleSetJars", KtLintSyncRuleSetJarTask::class.java) { sync ->

      val jarFolder = buildDir.resolve("ktlint-rules-jars")
      val xml = file(".idea/ktlint.xml")

      sync.jarFolder.set(jarFolder)
      sync.xmlFile.set(xml)

      sync.onlyIf { xml.exists() }

      val noTransitive = configProvider.map { original ->
        val copy = original.copy()

        copy.dependencies.clear()

        copy.dependencies.addAll(
          original.dependencies.map { dep ->
            when (dep) {
              is ModuleDependency -> dep.copy().also { it.setTransitive(false) }
              else -> dep.copy()
            }
          }
        )
        copy
      }

      sync.from(noTransitive)
      sync.into(jarFolder)
    }
      .addAsDependencyTo(tasks.named("prepareKotlinBuildScriptModel"))
  }

  private fun Project.sourceFileShadowDirectory(
    sourceSetName: String
  ): Provider<Directory> = layout.buildDirectory.dir("outputs/ktlint/$sourceSetName")
}
