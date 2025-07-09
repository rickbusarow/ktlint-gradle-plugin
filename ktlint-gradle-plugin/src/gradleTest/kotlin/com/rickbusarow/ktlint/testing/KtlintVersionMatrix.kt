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

import com.rickbusarow.kase.KaseMatrix
import com.rickbusarow.kase.gradle.AbstractDependencyVersion
import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.KotlinDependencyVersion
import com.rickbusarow.ktlint.GradleTestBuildConfig

internal class KtlintVersionMatrix(
  ktlint: List<KtlintDependencyVersion> = ktlintList,
  kotlin: List<KotlinDependencyVersion> = kotlinList,
  gradle: List<GradleDependencyVersion> = gradleList
) : KaseMatrix by KaseMatrix(ktlint + kotlin + gradle) {
  private companion object {
    val ktlintList = setOf(
      "1.1.0",
      "1.2.0",
      GradleTestBuildConfig.ktlintVersion
    )
      .sorted()
      .map(::KtlintDependencyVersion)

    val kotlinList = setOf(
      "2.0.0",
      "2.2.0",
      KotlinDependencyVersion.current().value
    )
      .sorted()
      .map(::KotlinDependencyVersion)

    val gradleList = setOf(
      "8.11",
      GradleDependencyVersion.current().value
    )
      .map(::GradleDependencyVersion)
      .sorted()
  }
}

class KtlintDependencyVersion(
  override val value: String
) : AbstractDependencyVersion<KtlintDependencyVersion, KtlintDependencyVersion.KtlintKey>(KtlintKey) {
  companion object KtlintKey : KaseMatrix.KaseMatrixKey<KtlintDependencyVersion>
}

interface HasKtlintDependencyVersion {
  val ktlint: KtlintDependencyVersion

  val ktlintVersion: String

  companion object {
    operator fun invoke(
      version: KtlintDependencyVersion
    ): HasKtlintDependencyVersion = object : HasKtlintDependencyVersion {
      override val ktlint: KtlintDependencyVersion = version
      override val ktlintVersion: String = version.value
    }
  }
}
