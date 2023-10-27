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

import com.rickbusarow.kgx.addAsDependencyTo
import com.rickbusarow.kgx.buildDir
import com.rickbusarow.kgx.dependsOn
import com.rickbusarow.kgx.internal.InternalGradleApiAccess
import com.rickbusarow.kgx.internal.whenElementKnown
import com.rickbusarow.kgx.internal.whenElementRegistered
import com.rickbusarow.kgx.isRootProject
import com.rickbusarow.kgx.outputsUpToDateWhen
import com.rickbusarow.kgx.registerOnce
import com.rickbusarow.kgx.withAnyPlugin
import com.rickbusarow.ktlint.internal.GradleConfiguration
import com.rickbusarow.ktlint.internal.GradleProject
import com.rickbusarow.ktlint.internal.GradleProvider
import com.rickbusarow.ktlint.internal.KtLintOutputHashes.Companion.readFormatOutput
import com.rickbusarow.ktlint.internal.capitalize
import com.rickbusarow.ktlint.internal.decapitalize
import com.rickbusarow.ktlint.internal.existsOrNull
import com.rickbusarow.ktlint.internal.flatMapToSet
import com.rickbusarow.ktlint.internal.kotlinExtension
import com.rickbusarow.ktlint.internal.mapToSet
import com.rickbusarow.ktlint.internal.md5
import com.rickbusarow.ktlint.internal.remove
import com.rickbusarow.ktlint.internal.resolveInParentOrNull
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.DefaultTaskOutputs
import org.gradle.api.specs.AndSpec
import org.gradle.api.specs.Spec
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.io.File

/** @since 0.1.1 */
@Suppress("UnnecessaryAbstractClass")
abstract class KtLintPlugin : Plugin<GradleProject> {

  override fun apply(target: GradleProject) {

    val rulesetConfig: NamedDomainObjectProvider<GradleConfiguration> = target.configurations
      .register("ktlint") { config ->

        config.dependencies.also { defaultDeps ->

          val deps = BuildConfig.deps
            .split(",")
            .map { it.trim() }

          for (coords in deps) {
            defaultDeps.add(target.dependencies.create(coords))
          }
        }
      }

    val configProvider: NamedDomainObjectProvider<GradleConfiguration> = target.configurations
      .register("ktlintAllDependencies") {
        it.extendsFrom(rulesetConfig.get())
      }

    var kotlinHappened = false

    target.withKotlinPlugin {
      doApply(target, rulesetConfig, configProvider)
      kotlinHappened = true
    }

    if (!kotlinHappened) {
      target.afterEvaluate {
        if (!kotlinHappened) {
          doApply(it, rulesetConfig, configProvider)
        }
      }
    }
  }

  @OptIn(InternalGradleApiAccess::class)
  private fun doApply(
    target: GradleProject,
    rulesetConfig: NamedDomainObjectProvider<GradleConfiguration>,
    configProvider: NamedDomainObjectProvider<GradleConfiguration>
  ) {

    val editorConfigFile = lazy {
      target.projectDir.resolveInParentOrNull(".editorconfig")
    }

    val rootTaskPair = registerFormatCheckPair(
      target = target,
      taskNameSuffix = "",
      sourceFiles = null,
      editorConfigFile = editorConfigFile,
      rulesetConfig = rulesetConfig
    )

    val scriptTaskPair = registerFormatCheckPair(
      target = target,
      taskNameSuffix = "GradleScripts",
      sourceFiles = target.provider {

        val buildDir = target.buildDir()
        val includedBuildDirs = target.gradle.includedBuilds.mapToSet { it.projectDir }
        val subProjectDirs = target.subprojects.mapToSet { it.projectDir }
        val srcDirs = target.kotlinExtension?.sourceSets
          ?.flatMapToSet { it.kotlin.sourceDirectories }
          .orEmpty()
        val reg = Regex(""".*\.gradle\.kts$""")

        target.files(
          target.projectDir
            .walkTopDown()
            .onEnter {
              when (it) {
                buildDir -> false
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
      rulesetConfig = rulesetConfig
    )

    rootTaskPair.first.dependsOn(scriptTaskPair.first)
    rootTaskPair.second.dependsOn(scriptTaskPair.second)

    target.withKotlinPlugin {

      val sourceSets = target.kotlinExtension?.sourceSets
      sourceSets?.whenElementKnown { elementInfo ->
        registerKotlinTasks(
          target = target,
          rootTaskPair = rootTaskPair,
          editorConfigFile = editorConfigFile,
          rulesetConfig = rulesetConfig,
          sourceSetName = elementInfo.name,
          sourceSet = sourceSets.named(elementInfo.name)
        )
      }
    }

    if (target.isRootProject()) {
      target.registerSyncRuleSetJars(configProvider)
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
    rulesetConfig: NamedDomainObjectProvider<GradleConfiguration>?,
    sourceSetName: String,
    sourceSet: NamedDomainObjectProvider<KotlinSourceSet>
  ) {

    val pair = registerFormatCheckPair(
      target = target,
      taskNameSuffix = sourceSetName.capitalize(),
      sourceFiles = sourceSet.map { ss ->
        ss.kotlin.filter { file -> !file.startsWith(target.buildDir()) }
      },
      editorConfigFile = editorConfigFile,
      rulesetConfig = rulesetConfig
    )

    val formatTasks = pair.first
    val lintTasks = pair.second

    rootTaskPair.first.dependsOn(formatTasks)
    rootTaskPair.second.dependsOn(lintTasks)
  }

  @OptIn(InternalGradleApiAccess::class)
  private fun registerFormatCheckPair(
    target: GradleProject,
    taskNameSuffix: String,
    sourceFiles: GradleProvider<FileCollection>?,
    editorConfigFile: Lazy<File?>,
    rulesetConfig: NamedDomainObjectProvider<GradleConfiguration>?
  ): Pair<TaskProvider<KtLintFormatTask>, TaskProvider<KtLintCheckTask>> {

    val outputMapFileName = "ktlint/${taskNameSuffix.decapitalize().ifBlank { "root" }}-sha.bin"

    val outputMapFile = target.buildDir().resolve(outputMapFileName)

    val formatTaskName = "ktlintFormat${taskNameSuffix.capitalize()}"

    val formatTask = target.tasks
      .registerOnce(formatTaskName, KtLintFormatTask::class.java) { task ->

        val projectDir = target.projectDir

        task.onlyIf {
          it as AbstractKtLintTask

          val mapFile = it.outputHashFile.get().asFile

          val outputMap = mapFile.existsOrNull()?.readFormatOutput()

          when {
            outputMap == null -> {

              println("########################## onlyIf spec 237 --  true")
              return@onlyIf true
            }

            it.sourceFiles.isEmpty -> false

            else -> {
              it.sourceFiles.any { sourceFile ->
                println(
                  "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~  ${sourceFile.path}"
                )
                println("source md5 -- ${sourceFile.md5()}")
                println("output md5 -- ${outputMap[sourceFile.relativeTo(projectDir)]}")
                println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
                sourceFile.md5() != outputMap[sourceFile.relativeTo(projectDir)]
              }
                // TODO <Rick> delete me
                .also { any ->
                  println("########################## onlyIf spec 253 --  $any")
                }
            }
          }
        }

        val upToDateSpec: AndSpec<in TaskInternal> = task.outputs.upToDateSpec

        val op = task.outputs as DefaultTaskOutputs

        println(
          "########################## upToDateSpec specs  --  " + task.outputs.upToDateSpec
            .specs.map { it.isSatisfiedBy(task) }
        )

        val union: Spec<TaskInternal> = Specs.union(
          upToDateSpec.specs +
            Spec { (it as? KtLintFormatTask)?.changedFilesAreUnchanged() ?: false }
        )

        @Suppress("UNCHECKED_CAST")
        val specs = upToDateSpec.specs as MutableList<Spec<in TaskInternal>>

        // specs.clear()
        // specs.add(union)

        task.outputsUpToDateWhen { task2 ->

          val pof = try {
            task2.outputs.previousOutputFiles
          } catch (e: IllegalStateException) {
            println("~~~~~~ no previous output files ~~~~~~")
            emptySet<File>()
          }

          """
            #############################################
                                 exists -- ${outputMapFile.exists()}
                     outputHashFile property -- ${task2.outputHashFile.get()}
              outputHashFile property exists -- ${task2.outputHashFile.get().asFile.exists()}
                        output map file -- $outputMapFile
                           output files -- ${task2.outputs.files.files}
                  previous output files -- $pof
            #############################################
          """.trimIndent()
            .remove(projectDir.path)
            .also { println(it) }

          if (!outputMapFile.exists()) {
            return@outputsUpToDateWhen false
          }

          task2.changedFilesAreUnchanged()
        }

        task.sourceFiles.from(sourceFiles)

        task.ktlintClasspath.setFrom(rulesetConfig)

        task.outputHashFile.set(outputMapFile)
        task.editorConfig.fileValue(editorConfigFile.value)
      }
      .also { task ->
        target.tasks.whenElementRegistered("fix") {
          it.dependsOn(task)
        }
      }

    val checkTaskName = "ktlintCheck${taskNameSuffix.capitalize()}"

    val lintTask = target.tasks.registerOnce(checkTaskName, KtLintCheckTask::class.java) { task ->

      task.sourceFiles.from(sourceFiles)

      task.ktlintClasspath.setFrom(rulesetConfig)

      // If both tasks are running in the same invocation, make sure that the format task runs first.
      task.mustRunAfter(formatTask)

      task.outputHashFile.set(outputMapFile)
      task.editorConfig.fileValue(editorConfigFile.value)
    }
      .also { task ->
        target.tasks.whenElementRegistered("check") {
          it.dependsOn(task)
        }
      }

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

  internal companion object {
    const val TASK_GROUP = "KtLint"
  }
}
