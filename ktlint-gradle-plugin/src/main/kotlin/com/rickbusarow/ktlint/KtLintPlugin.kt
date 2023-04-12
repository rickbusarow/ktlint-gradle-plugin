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

import com.rickbusarow.ktlint.internal.capitalize
import com.rickbusarow.ktlint.internal.resolveInParentOrNull
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

abstract class KtLintPlugin : Plugin<Project> {
  override fun apply(target: Project) {

    val configProvider: NamedDomainObjectProvider<Configuration> =
      target.configurations.register("ktlint")

    target.plugins.withId("org.jetbrains.kotlin.jvm") {
      registerKotlinTasks(target, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.android") {
      registerKotlinTasks(target, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.js") {
      registerKotlinTasks(target, configProvider)
    }
    target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
      registerKotlinTasks(target, configProvider)
    }

    if (target == target.rootProject) {

      target.tasks.register("ktlintFormat", KtlintFormatTask::class.java) { task ->

        task.sourceFiles.setFrom()

        task.ktlintClasspath.setFrom(configProvider)

        task.sourceFilesShadow.set(target.buildDir.resolve("outputs/ktlint/root"))
        task.editorConfig.fileValue(target.projectDir.resolveInParentOrNull(".editorconfig"))
      }

      target.tasks.register("ktlintCheck", KtlintCheckTask::class.java) { task ->

        task.sourceFiles.setFrom()

        task.ktlintClasspath.setFrom(configProvider)

        task.sourceFilesShadow.set(target.buildDir.resolve("outputs/ktlint/root"))
        task.editorConfig.fileValue(target.projectDir.resolveInParentOrNull(".editorconfig"))
      }
    }

    if (target == target.rootProject) {
      target.tasks.register("syncRuleSetJars", KtLintSyncRuleSetJarTask::class.java) { sync ->

        val jarFolder = target.buildDir.resolve("ktlint-rules-jars")
        val xml = target.rootProject.file(".idea/ktlint.xml")

        sync.jarFolder.set(jarFolder)
        sync.xmlFile.set(xml)

        sync.inputs.file(xml)

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

      target.tasks.named("prepareKotlinBuildScriptModel") {
        it.dependsOn("syncRuleSetJars")
      }
    }

    // target.dependencies.add("ktlint", target.libsCatalog.dependency("ktlint-ruleset-experimental"))
    // target.dependencies.add("ktlint", target.libsCatalog.dependency("ktlint-ruleset-standard"))
  }

  private fun registerKotlinTasks(
    target: Project,
    configProvider: NamedDomainObjectProvider<Configuration>?
  ) {
    val extension = target.extensions.getByType(KotlinProjectExtension::class.java)

    val sourceSetNames = extension.sourceSets.names

    val pairs = sourceSetNames.map { sourceSetName ->
      registerFormatCheckPair(target, extension, sourceSetName, configProvider)
    }

    val formatTasks = pairs.map { it.first }

    val lintTasks = pairs.map { it.second }

    target.tasks.register("ktlintFormat", KtlintTask::class.java) { task ->

      task.sourceFiles.setFrom()

      task.ktlintClasspath.setFrom(configProvider)

      task.dependsOn(formatTasks)

      task.sourceFilesShadow.set(target.buildDir.resolve("outputs/ktlint/root"))
      task.editorConfig.fileValue(target.projectDir.resolveInParentOrNull(".editorconfig"))
    }

    target.tasks.register("ktlintCheck", KtlintCheckTask::class.java) { task ->

      task.sourceFiles.setFrom()

      task.ktlintClasspath.setFrom(configProvider)

      task.dependsOn(lintTasks)

      task.sourceFilesShadow.set(target.buildDir.resolve("outputs/ktlint/root"))
      task.editorConfig.fileValue(target.projectDir.resolveInParentOrNull(".editorconfig"))
    }
  }

  private fun registerFormatCheckPair(
    target: Project,
    extension: KotlinProjectExtension,
    sourceSetName: String,
    configProvider: NamedDomainObjectProvider<Configuration>?
  ): Pair<TaskProvider<KtlintTask>, TaskProvider<KtlintTask>> {

    val formatTaskName = "ktlintFormat${sourceSetName.capitalize()}"

    val formatTask = target.tasks.register(formatTaskName, KtlintTask::class.java) { task ->

      val sourceSet = extension.sourceSets.getByName(sourceSetName)

      task.sourceFiles.from(sourceSet.kotlin)

      task.ktlintClasspath.setFrom(configProvider)

      task.sourceFilesShadow.set(target.buildDir.resolve("outputs/ktlint/$sourceSetName"))
      task.editorConfig.fileValue(target.projectDir.resolveInParentOrNull(".editorconfig"))
    }

    val checkTaskName = "ktlintCheck${sourceSetName.capitalize()}"

    val lintTask = target.tasks.register(checkTaskName, KtlintTask::class.java) { task ->

      val sourceSet = extension.sourceSets.getByName(sourceSetName)

      task.sourceFiles.from(sourceSet.kotlin)

      task.ktlintClasspath.setFrom(configProvider)

      task.mustRunAfter(formatTask)

      task.sourceFilesShadow.set(target.buildDir.resolve("outputs/ktlint/$sourceSetName"))
      task.editorConfig.fileValue(target.projectDir.resolveInParentOrNull(".editorconfig"))
    }

    return formatTask to lintTask
  }
}
