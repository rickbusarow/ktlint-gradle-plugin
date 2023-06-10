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

// TODO: https://youtrack.jetbrains.com/issue/KTIJ-23114/
@file:Suppress("invisible_reference", "invisible_member")

package com.rickbusarow.ktlint

import com.rickbusarow.ktlint.internal.createSafely
import com.rickbusarow.ktlint.internal.div
import com.rickbusarow.ktlint.internal.letIf
import com.rickbusarow.ktlint.internal.suffixIfNot
import com.rickbusarow.ktlint.testing.HasWorkingDir
import com.rickbusarow.ktlint.testing.HasWorkingDir.Companion.testStackTraceElement
import com.rickbusarow.ktlint.testing.SkipInStackTrace
import com.rickbusarow.ktlint.testing.TrimmedAsserts
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldNotBe
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD
import java.io.File
import io.kotest.matchers.string.shouldInclude as kotestShouldInclude

@Execution(SAME_THREAD)
internal interface BaseGradleTest : TrimmedAsserts {

  class GradleTestEnvironment(
    testStackFrame: StackTraceElement
  ) : HasWorkingDir(createWorkingDir(testStackFrame)), TrimmedAsserts {

    val buildFile by lazy {
      workingDir.resolve("build.gradle.kts").createSafely(
        """
        plugins {
          id("com.rickbusarow.ktlint") version "${BuildConfig.version}"
        }

        """.trimIndent()
      )
    }

    val settingsFile by lazy {
      workingDir.resolve("settings.gradle.kts")
        .createSafely(
          """
          rootProject.name = "root"

          pluginManagement {
            repositories {
              mavenLocal()
              mavenCentral()
              gradlePluginPortal()
              google()
              maven("https://plugins.gradle.org/m2/")
            }
            plugins {
              id("org.jetbrains.kotlin.jvm") version "${BuildConfig.kotlinVersion}"
              id("${BuildConfig.pluginId}") version "${BuildConfig.version}"
            }
          }
          dependencyResolutionManagement {
            repositories {
              mavenLocal()
              mavenCentral()
              google()
              maven("https://plugins.gradle.org/m2/")
            }
          }

          """.trimIndent()
        )
    }

    private val gradleRunner: GradleRunner by lazy {

      GradleRunner.create()
        .forwardOutput()
        .withGradleVersion(BuildConfig.gradleVersion)
        .withDebug(true)
        .withProjectDir(workingDir)
    }

    private fun build(
      tasks: List<String>,
      withPluginClasspath: Boolean,
      withHermeticTestKit: Boolean,
      stacktrace: Boolean,
      shouldFail: Boolean
    ): BuildResult {
      ensureFilesAreWritten()
      return gradleRunner
        .letIf(withPluginClasspath) { withPluginClasspath() }
        .letIf(withHermeticTestKit) { withTestKitDir(workingDir / "testKit") }
        .withArguments(tasks.letIf(stacktrace) { plus("--stacktrace") })
        .let { runner ->
          if (shouldFail) {
            runner.buildAndFail()
          } else {
            runner.build()
              .also { result ->
                result.tasks
                  .forAll { buildTask ->
                    buildTask.outcome shouldNotBe TaskOutcome.FAILED
                  }
              }
          }
        }
    }

    inline fun shouldSucceed(
      vararg tasks: String,
      withPluginClasspath: Boolean = false,
      withHermeticTestKit: Boolean = false,
      stacktrace: Boolean = true,
      assertions: BuildResult.() -> Unit = {}
    ): BuildResult {

      return build(
        tasks.toList(),
        withPluginClasspath = withPluginClasspath,
        withHermeticTestKit = withHermeticTestKit,
        stacktrace = stacktrace,
        shouldFail = false
      ).also { result ->
        result.assertions()
      }
    }

    fun clean() {

      workingDir.deleteRecursively()
    }

    private fun ensureFilesAreWritten() {
      buildFile
      settingsFile
      workingDir.walkTopDown()
        .filter { !it.path.contains("/testKit/") }
        .filter { it.isFile }
        .forEach { println("file://$it") }
    }

    inline fun shouldFail(
      vararg tasks: String,
      withPluginClasspath: Boolean = false,
      withHermeticTestKit: Boolean = false,
      stacktrace: Boolean = true,
      assertions: BuildResult.() -> Unit = {}
    ): BuildResult {
      return build(
        tasks.toList(),
        withPluginClasspath = withPluginClasspath,
        withHermeticTestKit = withHermeticTestKit,
        stacktrace = stacktrace,
        shouldFail = true
      ).also { result ->
        result.assertions()
      }
    }

    infix fun String.shouldInclude(expected: String) {

      if (File.separatorChar != '/') {
        replace(File.separator, "/").replace("(/n)", "(\\n)") kotestShouldInclude expected
      } else {
        this@shouldInclude kotestShouldInclude expected
      }
    }

    fun markdown(
      path: String,
      @Language("markdown") content: String
    ): File = File(path).createSafely(content.trimIndent())

    @JvmName("writeMarkdownContent")
    fun File.markdown(
      @Language("markdown") content: String
    ): File = createSafely(content.trimIndent())

    fun kotlin(
      path: String,
      @Language("kotlin") content: String
    ): File = File(path).createSafely(content.trimIndent().suffixIfNot("\n\n"))

    @JvmName("writeKotlinContent")
    fun File.kotlin(
      @Language("kotlin") content: String
    ): File = createSafely(content.trimIndent())

    operator fun File.invoke(contentBuilder: () -> String) {
      createSafely(contentBuilder().trimIndent())
    }
  }

  @SkipInStackTrace
  fun test(action: GradleTestEnvironment.() -> Unit) {

    val gradleTestEnvironment = GradleTestEnvironment(testStackTraceElement())

    gradleTestEnvironment.clean()
    gradleTestEnvironment.action()
  }
}
