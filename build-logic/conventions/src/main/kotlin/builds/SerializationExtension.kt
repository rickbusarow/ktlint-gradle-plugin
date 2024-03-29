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

import com.rickbusarow.kgx.dependency
import com.rickbusarow.kgx.libsCatalog
import org.gradle.api.Project

interface SerializationExtension {

  fun Project.serialization() {
    if (!pluginManager.hasPlugin("org.jetbrains.kotlin.plugin.serialization")) {

      pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")

      dependencies.constraints
        .add(
          "implementation",
          libsCatalog.dependency("kotlinx-serialization-core")
        )
    }
  }
}
