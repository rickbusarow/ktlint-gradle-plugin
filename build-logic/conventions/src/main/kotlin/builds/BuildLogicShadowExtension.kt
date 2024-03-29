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

package builds

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.rickbusarow.kgx.applyOnce
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

interface BuildLogicShadowExtension {

  fun Project.shadow(shadowConfiguration: Configuration? = null) {

    if (shadowConfiguration != null) {
      configurations.named("compileOnly") { it.extendsFrom(shadowConfiguration) }
    }

    plugins.applyOnce("com.github.johnrengelman.shadow")

    val shadowJar = tasks.named("shadowJar", ShadowJar::class.java) { task ->

      if (shadowConfiguration != null) {
        task.configurations = listOf(shadowConfiguration)

        listOf(
          "org.intellij.markdown",
          "org.jetbrains.kotlin"
        ).forEach {
          task.relocate(it, "com.rickbusarow.ktlint.$it")
        }

        val classifier = if (plugins.hasPlugin("java-gradle-plugin")) "" else "all"

        task.archiveClassifier.set(classifier)

        // task.transformers.add(ServiceFileTransformer())

        task.minimize()

        task.exclude(
          "**/*.kotlin_metadata",
          "**/*.kotlin_module",
          "META-INF/maven/**"
        )
      }
    }

    // By adding the task's output to archives, it's automatically picked up by Gradle's maven-publish
    // plugin and added as an artifact to the publication.
    artifacts {
      it.add("runtimeOnly", shadowJar)
      it.add("archives", shadowJar)
    }
  }
}
