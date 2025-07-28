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

package com.rickbusarow.ktlint.testing

import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.JavaFileFileInjection
import com.rickbusarow.kase.files.LanguageInjection
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.gradle.DefaultGradleTestEnvironment
import com.rickbusarow.kase.gradle.DslLanguage
import com.rickbusarow.kase.gradle.GradleProjectBuilder
import com.rickbusarow.kase.gradle.GradleRootProjectBuilder
import com.rickbusarow.kase.gradle.GradleTestEnvironmentFactory
import com.rickbusarow.kase.gradle.HasAgpDependencyVersion
import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.dsl.SettingsFileSpec
import com.rickbusarow.kase.gradle.rootProject
import com.rickbusarow.kase.stdlib.letIf
import com.rickbusarow.kase.stdlib.mapLines
import com.rickbusarow.ktlint.GradleTestBuildConfig
import com.rickbusarow.ktlint.internal.Ansi.Companion.noAnsi
import org.gradle.testkit.runner.BuildResult
import java.io.File

internal class KtlintGradleTestEnvironment(
  private val params: KtlintGradleTestParams,
  override val dslLanguage: DslLanguage,
  hasWorkingDir: HasWorkingDir,
  override val rootProject: GradleRootProjectBuilder
) : DefaultGradleTestEnvironment(
  gradleVersion = params.gradle,
  dslLanguage = dslLanguage,
  hasWorkingDir = hasWorkingDir,
  rootProject = rootProject
),
  LanguageInjection<File> by LanguageInjection(JavaFileFileInjection()) {

  val ktlintVersion: String
    get() = params.ktlintVersion

  val GradleProjectBuilder.buildFileAsFile: File
    get() = path.resolve(dslLanguage.buildFileName)
  val GradleProjectBuilder.settingsFileAsFile: File
    get() = path.resolve(dslLanguage.settingsFileName)

  val BuildResult.outputCleaned: String
    get() {
      val sep = File.separatorChar
      return output
        .noAnsi()
        .mapLines { line ->
          if (!line.trim().startsWith("file://")) return@mapLines line

          line.removePrefix("file://${workingDir.path}$sep")
            .letIf(sep == '\\') { windowsLine ->
              windowsLine
                .split(" ", limit = 2)
                .let { (path, rest) ->
                  "${path.replace(sep, '/')} $rest"
                }
            }
        }
    }

  class Factory : GradleTestEnvironmentFactory<KtlintGradleTestParams, KtlintGradleTestEnvironment> {

    override val localM2Path: File
      get() = GradleTestBuildConfig.localBuildM2Dir

    override fun buildFileDefault(versions: KtlintGradleTestParams): BuildFileSpec =
      BuildFileSpec {
        plugins {
          kotlin("jvm", versions.kotlinVersion)
          id("com.rickbusarow.ktlint", version = GradleTestBuildConfig.version)
        }

        raw(
          """
            ktlint {
              ktlintVersion.set("${versions.ktlintVersion}")
            }
          """.trimIndent()
        )
      }

    override fun settingsFileDefault(versions: KtlintGradleTestParams): SettingsFileSpec {
      return SettingsFileSpec {
        rootProjectName.setEquals("root")

        pluginManagement {
          repositories {
            maven(localM2Path)
            gradlePluginPortal()
            mavenCentral()
            google()
          }
          plugins {
            kotlin("jvm", version = versions.kotlinVersion, apply = false)
            id("com.rickbusarow.ktlint", version = GradleTestBuildConfig.version)
            if (versions is HasAgpDependencyVersion) {
              id("com.android.application", versions.agpVersion, apply = false)
              id("com.android.library", versions.agpVersion, apply = false)
            }
          }
        }
        dependencyResolutionManagement {
          repositories {
            maven(localM2Path)
            gradlePluginPortal()
            mavenCentral()
            google()
          }
        }
      }
    }

    override fun create(
      params: KtlintGradleTestParams,
      names: List<String>,
      location: TestLocation
    ): KtlintGradleTestEnvironment {
      val hasWorkingDir = HasWorkingDir(names, location)
      val dslLanguage = DslLanguage.KotlinDsl(useInfix = true, useLabels = false)
      return KtlintGradleTestEnvironment(
        params = params,
        dslLanguage = dslLanguage,
        hasWorkingDir = hasWorkingDir,
        rootProject = rootProject(
          path = hasWorkingDir.workingDir,
          dslLanguage = dslLanguage
        ) {
          buildFile(buildFileDefault(params))
          settingsFile(settingsFileDefault(params))
        }

      )
    }
  }
}
