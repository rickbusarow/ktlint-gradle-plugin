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

import com.rickbusarow.ktlint.internal.GradleConfiguration
import com.rickbusarow.ktlint.internal.GradleProject
import com.rickbusarow.ktlint.internal.GradleProvider
import com.rickbusarow.ktlint.internal.addAsDependencyTo
import com.rickbusarow.ktlint.internal.capitalize
import com.rickbusarow.ktlint.internal.dependsOn
import com.rickbusarow.ktlint.internal.flatMapToSet
import com.rickbusarow.ktlint.internal.isRootProject
import com.rickbusarow.ktlint.internal.mapToSet
import com.rickbusarow.ktlint.internal.matchingName
import com.rickbusarow.ktlint.internal.registerOnce
import com.rickbusarow.ktlint.internal.resolveInParentOrNull
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

/** @since 0.1.1 */
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintPlugin : Plugin<GradleProject> {
  override fun apply(target: GradleProject) {

    val rulesetConfig: NamedDomainObjectProvider<GradleConfiguration> = target.configurations
      .register("ktlint")

    val configProvider: NamedDomainObjectProvider<GradleConfiguration> = target.configurations
      .register("ktlintAllDependencies") {
        it.extendsFrom(rulesetConfig.get())
      }

    BuildConfig.deps.split(",")
      .map { it.trim() }
      .forEach { coords ->
        target.dependencies.add("ktlintAllDependencies", coords)
      }

    val editorConfigFile = lazy {
      target.projectDir.resolveInParentOrNull(".editorconfig")
    }

    val rootTasks = registerFormatCheckTasks(
      target = target,
      taskNameSuffix = "",
      sourceDirectories = null,
      sourceFiles = null,
      editorConfigFile = editorConfigFile,
      intermediateSubdir = "root",
      configProvider = configProvider
    )

    val reg = Regex(""".*\.gradle\.kts$""")

    val scriptTasks = registerFormatCheckTasks(
      target = target,
      taskNameSuffix = "GradleScripts",
      sourceDirectories = null,
      sourceFiles = target.provider {

        val includedBuildDirs = target.gradle.includedBuilds.map { it.projectDir }
        val subProjectDirs = target.subprojects.mapToSet { it.projectDir }
        val srcDirs = target.getSourceSets().values.flatMapToSet { it.get().kotlin.srcDirs }

        target.files(
          target.projectDir
            .walkTopDown()
            .onEnter {
              when (it) {
                target.buildDir -> false
                target.file("src") -> false
                in includedBuildDirs -> false
                in subProjectDirs -> false
                in srcDirs -> false
                else -> true
              }
            }
            .filter { it.name.matches(reg) }.toList()
        )
      },
      editorConfigFile = editorConfigFile,
      intermediateSubdir = "scripts",
      configProvider = configProvider
    )

    rootTasks.intermediateTask.dependsOn(scriptTasks.intermediateTask)
    rootTasks.formatTask.dependsOn(scriptTasks.formatTask)
    rootTasks.checkTask.dependsOn(scriptTasks.checkTask)

    target.plugins.withId("org.jetbrains.kotlin.jvm") {
      registerKotlinTasks(target, rootTasks, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.android") {
      registerKotlinTasks(target, rootTasks, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.js") {
      registerKotlinTasks(target, rootTasks, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
      registerKotlinTasks(target, rootTasks, editorConfigFile, configProvider)
    }

    if (target.isRootProject()) {
      target.registerSyncRuleSetJars(rulesetConfig)
    }
  }

  private fun registerKotlinTasks(
    target: GradleProject,
    rootTasks: FormatCheckTasks,
    editorConfigFile: Lazy<File?>,
    configProvider: NamedDomainObjectProvider<GradleConfiguration>?
  ) {

    val pairs = target.getSourceSets()
      .map { (sourceSetName, sourceSet) ->
        registerFormatCheckTasks(
          target = target,
          taskNameSuffix = sourceSetName.capitalize(),
          sourceDirectories = sourceSet.map { it.kotlin.sourceDirectories },
          sourceFiles = sourceSet.map {
            it.kotlin.filter { file -> !file.startsWith(target.buildDir) }
          },
          editorConfigFile = editorConfigFile,
          intermediateSubdir = sourceSetName,
          configProvider = configProvider
        )
      }

    val intermediateTasks = pairs.map { it.intermediateTask }
    val formatTasks = pairs.map { it.formatTask }
    val lintTasks = pairs.map { it.checkTask }

    rootTasks.intermediateTask.dependsOn(intermediateTasks)
    rootTasks.formatTask.dependsOn(formatTasks)
    rootTasks.checkTask.dependsOn(lintTasks)
  }

  private fun GradleProject.getSourceSets(): Map<String, NamedDomainObjectProvider<KotlinSourceSet>> {

    val kotlinExtension = try {
      val extension = extensions.findByName("kotlin")

      extension as? org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
    } catch (_: ClassNotFoundException) {
      return emptyMap()
    } catch (_: NoClassDefFoundError) {
      return emptyMap()
    }

    return kotlinExtension?.sourceSets
      ?.names
      ?.associateWith { kotlinExtension.sourceSets.named(it) }
      .orEmpty()
  }

  class FormatCheckTasks(
    val intermediateTask: TaskProvider<KtLintFormatIntermediateTask>,
    val formatTask: TaskProvider<KtLintFormatTask>,
    val checkTask: TaskProvider<KtLintCheckTask>
  )

  private fun registerFormatCheckTasks(
    target: GradleProject,
    taskNameSuffix: String,
    sourceDirectories: GradleProvider<FileCollection>?,
    sourceFiles: GradleProvider<FileCollection>?,
    editorConfigFile: Lazy<File?>,
    intermediateSubdir: String,
    configProvider: NamedDomainObjectProvider<GradleConfiguration>?
  ): FormatCheckTasks {

    val intermediateFilesDir = target.layout.buildDirectory
      .dir("ktlint${File.separatorChar}$intermediateSubdir")

    val rootDir = target.projectDir

    val formatIntermediateTask = target.tasks
      .registerOnce(
        "ktlintFormatIntermediate${taskNameSuffix.capitalize()}",
        KtLintFormatIntermediateTask::class.java
      ) { task ->

        task.sourceFiles.from(sourceFiles)
        task.intermediateDirectory.set(intermediateFilesDir)
        task.projectRootDirectory.set(rootDir)
      }

    val formatTask = target.tasks
      .registerOnce(
        "ktlintFormat${taskNameSuffix.capitalize()}",
        KtLintFormatTask::class.java
      ) { task ->

        if (sourceDirectories != null) {
          task.sourceFiles.set(sourceDirectories)
        } else {
          task.sourceFiles.set(target.files())
        }

        task.ktlintClasspath.setFrom(configProvider)

        task.dependsOn(formatIntermediateTask)
        task.intermediateFiles.from(formatIntermediateTask)
        task.rootDir.set(rootDir)
        task.editorConfig.fileValue(editorConfigFile.value)
      }
      .addAsDependencyTo(target.tasks.matchingName("fix"))

    val lintTask = target.tasks
      .registerOnce(
        "ktlintCheck${taskNameSuffix.capitalize()}",
        KtLintCheckTask::class.java
      ) { task ->

        task.sourceFiles.from(sourceFiles)

        task.ktlintClasspath.setFrom(configProvider)

        // If both tasks are running in the same invocation,
        // make sure that the format task runs first.
        task.mustRunAfter(formatTask)

        task.rootDir.set(rootDir)
        task.editorConfig.fileValue(editorConfigFile.value)
      }
      .addAsDependencyTo(target.tasks.matchingName("check"))

    return FormatCheckTasks(
      intermediateTask = formatIntermediateTask,
      formatTask = formatTask,
      checkTask = lintTask
    )
  }

  private fun GradleProject.registerSyncRuleSetJars(
    configProvider: NamedDomainObjectProvider<GradleConfiguration>
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
}
