/*
 * Copyright (C) 2025 Rick Busarow
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

import com.rickbusarow.kgx.buildDir
import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.internal.InternalGradleApiAccess
import com.rickbusarow.kgx.internal.whenElementRegistered
import com.rickbusarow.kgx.withAnyPlugin
import com.rickbusarow.ktlint.internal.GradleConfiguration
import com.rickbusarow.ktlint.internal.GradleProject
import com.rickbusarow.ktlint.internal.GradleProvider
import com.rickbusarow.ktlint.internal.capitalize
import com.rickbusarow.ktlint.internal.flatMapToSet
import com.rickbusarow.ktlint.internal.mapToSet
import com.rickbusarow.ktlint.internal.resolveInParentOrNull
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetContainer
import java.io.File
import javax.inject.Inject

/** @since 0.2.2 */
open class KtLintExtension @Inject constructor(objects: ObjectFactory) {
  /**
   * The version of the core KtLint library to use
   *
   * @since 0.2.2
   */
  val ktlintVersion: Property<String> = objects.property(String::class.java)
    .convention(BuildConfig.ktlintVersion)
}

/** @since 0.1.1 */
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintPlugin : Plugin<GradleProject> {
  override fun apply(target: GradleProject) {

    val extension = target.extensions.create("ktlint", KtLintExtension::class.java)

    val rulesetConfig: NamedDomainObjectProvider<GradleConfiguration> = target.configurations
      .register("ktlint") { config ->

        config.dependencies.also { defaultDeps ->

          val deps = BuildConfig.deps

          for (coords in deps) {
            defaultDeps.add(target.dependencies.create(coords))
          }

          val ktlintDeps = extension.ktlintVersion.map { version ->
            BuildConfig.ktlintDeps
              .map { module ->
                target.dependencies.create("${module.trim()}:$version")
              }
          }

          defaultDeps.addAllLater(ktlintDeps)
        }
      }

    var kotlinHappened = false

    target.withKotlinPlugin {
      doApply(target, rulesetConfig)
      kotlinHappened = true
    }

    if (!kotlinHappened) {
      target.afterEvaluate {
        if (!kotlinHappened) {
          doApply(it, rulesetConfig)
        }
      }
    }
  }

  private fun doApply(
    target: GradleProject,
    rulesetConfig: NamedDomainObjectProvider<GradleConfiguration>
  ) {

    val editorConfigFile = lazy {
      target.projectDir.resolveInParentOrNull(".editorconfig")
    }

    val rootTaskPair = registerFormatCheckPair(
      target = target,
      taskNameSuffix = "",
      sourceFileShadowDirectory = target.sourceFileShadowDirectory("ktlintRoot"),
      sourceDirectorySet = target.files(),
      editorConfigFile = editorConfigFile,
      configProvider = rulesetConfig
    )

    val projectDir = target.projectDir

    val buildDir = target.buildDir()
    val includedBuildDirs = target.gradle.includedBuilds.mapToSet { it.projectDir }
    val subProjectDirs = target.subprojects.mapToSet { it.projectDir }

    val srcDirs = target.getSourceDirectories().values.flatMapToSet { it }

    val gradleReg = Regex(""".*\.gradle\.kts$""")
    val scriptTaskPair = registerFormatCheckPair(
      target = target,
      taskNameSuffix = "GradleScripts",
      sourceFileShadowDirectory = target.sourceFileShadowDirectory("gradleScripts"),
      sourceDirectorySet = target.files(
        projectDir.walkTopDown()
          .onEnter {
            when (it) {
              buildDir -> false
              projectDir.resolve("src") -> false
              in includedBuildDirs -> false
              in subProjectDirs -> false
              in srcDirs -> false
              else -> true
            }
          }
          .filter { it.name.matches(gradleReg) }.toList()
      ),
      editorConfigFile = editorConfigFile,
      configProvider = rulesetConfig
    )

    rootTaskPair.first.dependsOn(scriptTaskPair.first)
    rootTaskPair.second.dependsOn(scriptTaskPair.second)

    target.withKotlinPlugin {
      registerKotlinTasks(target, rootTaskPair, editorConfigFile, rulesetConfig)
    }
  }

  private inline fun GradleProject.withKotlinPlugin(crossinline action: () -> Unit) {

    pluginManager.withAnyPlugin(
      "org.jetbrains.kotlin.jvm",
      "org.jetbrains.kotlin.android",
      "org.jetbrains.kotlin.js",
      "org.jetbrains.kotlin.multiplatform"
    ) {
      action()
    }
  }

  private fun registerKotlinTasks(
    target: GradleProject,
    rootTaskPair: Pair<TaskProvider<KtLintFormatTask>, TaskProvider<KtLintCheckTask>>,
    editorConfigFile: Lazy<File?>,
    configProvider: NamedDomainObjectProvider<GradleConfiguration>?
  ) {

    val pairs = target.getSourceDirectories()
      .map { (sourceSetName, sourceSet) ->

        val layout = target.layout

        registerFormatCheckPair(
          target = target,
          taskNameSuffix = sourceSetName.capitalize(),
          sourceFileShadowDirectory = target.sourceFileShadowDirectory(sourceSetName),
          sourceDirectorySet = target.files(
            sourceSet.filter { file -> !file.startsWith(layout.buildDirectory.get().asFile.path) }
          ),
          editorConfigFile = editorConfigFile,
          configProvider = configProvider
        )
      }

    val formatTasks = pairs.map { it.first }
    val lintTasks = pairs.map { it.second }

    rootTaskPair.first.dependsOn(formatTasks)
    rootTaskPair.second.dependsOn(lintTasks)
  }

  private fun GradleProject.getSourceDirectories(): Map<String, FileCollection> = try {

    extensions
      .findByType(KotlinSourceSetContainer::class.java)
      ?.sourceSets
      .orEmpty()
      .associate { it.name to it.kotlin.sourceDirectories }
  } catch (_: ClassNotFoundException) {
    emptyMap()
  } catch (_: NoClassDefFoundError) {
    emptyMap()
  }

  @OptIn(InternalGradleApiAccess::class)
  private fun registerFormatCheckPair(
    target: GradleProject,
    taskNameSuffix: String,
    sourceFileShadowDirectory: GradleProvider<Directory>,
    sourceDirectorySet: ConfigurableFileCollection,
    editorConfigFile: Lazy<File?>,
    configProvider: NamedDomainObjectProvider<GradleConfiguration>?
  ): Pair<TaskProvider<KtLintFormatTask>, TaskProvider<KtLintCheckTask>> {

    val formatTaskName = "ktlintFormat${taskNameSuffix.capitalize()}"
    val checkTaskName = "ktlintCheck${taskNameSuffix.capitalize()}"

    fun <T : KtLintTask> TaskProvider<T>.configure() = apply {
      configure { task ->
        task.sourceFiles.from(sourceDirectorySet)
        task.ktlintClasspath.setFrom(configProvider)
        task.sourceFilesShadow.set(sourceFileShadowDirectory)
        task.rootDir.set(target.rootProject.layout.projectDirectory)
        task.editorConfig.fileValue(editorConfigFile.value)
      }
    }

    val formatTask = target.tasks
      .register(formatTaskName, KtLintFormatTask::class.java)
      .also { format ->
        target.tasks.whenElementRegistered("fix") { fix -> fix.dependsOn(format) }
      }
      .configure()

    val lintTask = target.tasks
      .register(checkTaskName, KtLintCheckTask::class.java) {
        // If both tasks are running in the same invocation, make sure that the format task runs first.
        it.mustRunAfter(formatTask)
      }
      .also { format ->
        target.tasks.whenElementRegistered("check") { check -> check.dependsOn(format) }
      }
      .configure()

    return formatTask to lintTask
  }

  private fun GradleProject.sourceFileShadowDirectory(
    sourceSetName: String
  ): GradleProvider<Directory> = layout.buildDirectory.dir("outputs/ktlint/$sourceSetName")
}
