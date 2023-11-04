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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  dependencies {
    classpath(libs.kotlin.gradle.plugin)
    classpath(libs.vanniktech.publish)
  }
}

plugins {
  base
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.moduleCheck)
  alias(libs.plugins.ktlint)
}

moduleCheck {
  deleteUnused = true
  checks.sortDependencies = true
}

allprojects {

  plugins.withId("java-base") {
    configure<JavaPluginExtension> {
      @Suppress("MagicNumber")
      toolchain.languageVersion.set(JavaLanguageVersion.of(11))
    }
  }

  afterEvaluate {

    dependencies {
      ktlint(libs.rickBusarow.ktrules)
    }
  }

  tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
      jvmTarget = "11"
    }
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }
}
