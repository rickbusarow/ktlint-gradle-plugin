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

import com.rickbusarow.kase.AbstractKase3
import com.rickbusarow.kase.Kase3
import com.rickbusarow.kase.KaseMatrix
import com.rickbusarow.kase.get
import com.rickbusarow.kase.gradle.GradleDependencyVersion
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.KotlinDependencyVersion
import com.rickbusarow.kase.gradle.TestVersionsFactory
import kotlin.LazyThreadSafetyMode.NONE

internal interface KtlintGradleTestParams :
  Kase3<GradleDependencyVersion, KotlinDependencyVersion, KtlintDependencyVersion>,
  HasKtlintDependencyVersion,
  GradleKotlinTestVersions {

  companion object : TestVersionsFactory<KtlintGradleTestParams> {

    override fun extract(matrix: KaseMatrix): List<KtlintGradleTestParams> = matrix.get(
      a1Key = GradleDependencyVersion,
      a2Key = KotlinDependencyVersion,
      a3Key = KtlintDependencyVersion,
      instanceFactory = { gradle, kotlin, ktlint ->
        DefaultKtlintGradleTestParams(
          gradle = gradle,
          kotlin = kotlin,
          ktlint = ktlint
        )
      }
    )
  }
}

internal class DefaultKtlintGradleTestParams(
  override val gradle: GradleDependencyVersion,
  override val kotlin: KotlinDependencyVersion,
  override val ktlint: KtlintDependencyVersion
) : AbstractKase3<GradleDependencyVersion, KotlinDependencyVersion, KtlintDependencyVersion>(
  a1 = gradle,
  a2 = kotlin,
  a3 = ktlint
),
  KtlintGradleTestParams {

  override val gradleVersion: String get() = gradle.value
  override val kotlinVersion: String get() = kotlin.value
  override val ktlintVersion: String get() = ktlint.value

  override val displayName: String by lazy(NONE) {
    "gradle: $gradle | kotlin: $kotlin | ktlint: $ktlint"
  }
}
