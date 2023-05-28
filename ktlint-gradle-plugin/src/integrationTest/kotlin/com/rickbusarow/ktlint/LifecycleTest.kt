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

import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.junit.jupiter.api.Test

@Suppress("RemoveEmptyClassBody")
internal class LifecycleTest : BaseGradleTest {

  @Test
  fun `the check lifecycle task invokes ktlintCheck`() = test {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")
      }
      """
    }

    workingDir.resolve("src/main/kotlin/com/test/File.kt")
      .kotlin(
        """
        package com.test

        class File

        """
      )

    shouldSucceed("check") {
      task(":ktlintCheck")?.outcome shouldBe SUCCESS
    }
  }

  @Test
  fun `the fix lifecycle task invokes ktlintFormat`() = test {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")
      }

      val fix by tasks.registering
      """
    }

    workingDir
      .resolve("src/main/kotlin/com/test/File.kt")
      .kotlin(
        """
        package com.test

        class File { }

        """
      )

    shouldSucceed("fix") {
      task(":ktlintFormat")?.outcome shouldBe SUCCESS
    }
  }

  @Test
  fun `the ktlintCheck must run after ktlintFormat if both are invoked`() = test {

    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")
      }

      val fix by tasks.registering
      """
    }

    workingDir
      .resolve("src/main/kotlin/com/test/File.kt")
      .kotlin(
        """
        package com.test

        class File { }

        """
      )

    shouldSucceed("ktlintCheck", "ktlintFormat") {
      tasks.map { it.path } shouldContainInOrder listOf(":ktlintFormat", ":ktlintCheck")
      task(":ktlintFormat")?.outcome shouldBe SUCCESS
      task(":ktlintCheck")?.outcome shouldBe SUCCESS
    }
  }
}
