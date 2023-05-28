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
import org.junit.jupiter.api.Test

@Suppress("FunctionName")
internal class ConfigTest : BaseGradleTest {

  @Test
  fun `groovy dsl config simple`() = test {

    val config =
      """
      // build.gradle
      plugins {
        id 'com.rickbusarow.doks' version '${BuildConfig.version}'
      }

      doks {
        // Define a set of documents with rules.
        dokSet {
          // Set the files which will be synced
          docs(projectDir) {
            include '**/*.md', '**/*.mdx'
          }

          // Define a rule used in updating.
          // This rule's name corresponds to the name used in documentation.
          rule('maven-artifact') {
            regex = maven('com\\.example\\.dino')
            // replace any maven coordinate string with one using the current version,
            // where '$1' is the group id, '$2' is the artifact id,
            // and 'CURRENT_VERSION' is just some variable.
            replacement = "\$1:\$2:${'$'}CURRENT_VERSION"
          }
        }
      }
      """.trimIndent()

    buildFile.resolveSibling("build.gradle")
      .createSafely(config.replace("doks {", "def CURRENT_VERSION = \"1.0.1\"\n\ndoks {"))

    buildFile.delete()

    workingDir.resolve("README.md")
      .markdown(
        """
        <!--doks maven-artifact:1-->
        ```kotlin
        dependencies {
          implementation("com.example.dino:sauropod:1.0.0")
        }
        ```
        <!--doks END-->
        """
      )

    shouldSucceed("doks")
  }
}
