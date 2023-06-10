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
import org.gradle.api.file.Directory
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

    val rootTaskPair = registerFormatCheckPair(
      target = target,
      taskNameSuffix = "",
      sourceFileShadowDirectory = target.sourceFileShadowDirectory("ktlintRoot"),
      sourceDirectorySet = null,
      editorConfigFile = editorConfigFile,
      configProvider = configProvider
    )

    val scriptTaskPair = registerFormatCheckPair(
      target = target,
      taskNameSuffix = "GradleScripts",
      sourceFileShadowDirectory = target.sourceFileShadowDirectory("gradleScripts"),
      sourceDirectorySet = target.provider {

        val includedBuildDirs = target.gradle.includedBuilds.map { it.projectDir }
        val subProjectDirs = target.subprojects.mapToSet { it.projectDir }
        val srcDirs = target.getSourceSets().values.flatMapToSet { it.get().kotlin.srcDirs }
        val reg = Regex(""".*\.gradle\.kts$""")
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
      configProvider = configProvider
    )

    rootTaskPair.first.dependsOn(scriptTaskPair.first)
    rootTaskPair.second.dependsOn(scriptTaskPair.second)

    target.plugins.withId("org.jetbrains.kotlin.jvm") {
      registerKotlinTasks(target, rootTaskPair, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.android") {
      registerKotlinTasks(target, rootTaskPair, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.js") {
      registerKotlinTasks(target, rootTaskPair, editorConfigFile, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
      registerKotlinTasks(target, rootTaskPair, editorConfigFile, configProvider)
    }

    if (target.isRootProject()) {
      target.registerSyncRuleSetJars(rulesetConfig)
    }
  }

  private fun registerKotlinTasks(
    target: GradleProject,
    rootTaskPair: Pair<TaskProvider<KtLintFormatTask>, TaskProvider<KtLintCheckTask>>,
    editorConfigFile: Lazy<File?>,
    configProvider: NamedDomainObjectProvider<GradleConfiguration>?
  ) {

    val pairs = target.getSourceSets()
      .map { (sourceSetName, sourceSet) ->
        registerFormatCheckPair(
          target = target,
          taskNameSuffix = sourceSetName.capitalize(),
          sourceFileShadowDirectory = target.sourceFileShadowDirectory(sourceSetName),
          sourceDirectorySet = sourceSet.map { it.kotlin.minus(target.fileTree(target.buildDir)) },
          editorConfigFile = editorConfigFile,
          configProvider = configProvider
        )
      }

    val formatTasks = pairs.map { it.first }
    val lintTasks = pairs.map { it.second }

    rootTaskPair.first.dependsOn(formatTasks)
    rootTaskPair.second.dependsOn(lintTasks)
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

  private fun registerFormatCheckPair(
    target: GradleProject,
    taskNameSuffix: String,
    sourceFileShadowDirectory: GradleProvider<Directory>,
    sourceDirectorySet: GradleProvider<FileCollection>?,
    editorConfigFile: Lazy<File?>,
    configProvider: NamedDomainObjectProvider<GradleConfiguration>?
  ): Pair<TaskProvider<KtLintFormatTask>, TaskProvider<KtLintCheckTask>> {

    val formatTaskName = "ktlintFormat${taskNameSuffix.capitalize()}"

    val formatTask =
      target.tasks.registerOnce(formatTaskName, KtLintFormatTask::class.java) { task ->

        task.sourceFiles.from(sourceDirectorySet)

        task.ktlintClasspath.setFrom(configProvider)

        task.sourceFilesShadow.set(sourceFileShadowDirectory)
        task.rootDir.set(target.rootProject.layout.projectDirectory)
        task.editorConfig.fileValue(editorConfigFile.value)
      }
        .addAsDependencyTo(target.tasks.matchingName("fix"))

    val checkTaskName = "ktlintCheck${taskNameSuffix.capitalize()}"

    val lintTask = target.tasks.registerOnce(checkTaskName, KtLintCheckTask::class.java) { task ->

      task.sourceFiles.from(sourceDirectorySet)

      task.ktlintClasspath.setFrom(configProvider)

      // If both tasks are running in the same invocation, make sure that the format task runs first.
      task.mustRunAfter(formatTask)

      task.sourceFilesShadow.set(sourceFileShadowDirectory)
      task.rootDir.set(target.rootProject.layout.projectDirectory)
      task.editorConfig.fileValue(editorConfigFile.value)
    }
      .addAsDependencyTo(target.tasks.matchingName("check"))

    return formatTask to lintTask
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

  private fun GradleProject.sourceFileShadowDirectory(
    sourceSetName: String
  ): GradleProvider<Directory> = layout.buildDirectory.dir("outputs/ktlint/$sourceSetName")
}
