/*
 * Copyright (C) 2024 Rick Busarow
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

import org.junit.jupiter.api.TestFactory

internal class KtLintVersionCompatibilityTest : BaseGradleTest {

  @TestFactory
  fun `different versions of ktlint are supported`() = listOf(
    "0.49.1",
    "1.0.0",
    "1.0.1",
    "1.1.0"
  ).test { version ->
    buildFile {
      """
      plugins {
        kotlin("jvm")
        id("com.rickbusarow.ktlint")
      }

      ktlint {
        ktlintVersion.set("$version")
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

    shouldSucceed("ktlintCheck")
    shouldSucceed("ktlintFormat")
  }
}
