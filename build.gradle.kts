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

import modulecheck.gradle.task.AbstractModuleCheckTask

plugins {
  id("root")
  alias(libs.plugins.moduleCheck)
}

moduleCheck {
  deleteUnused = true
  checks.sortDependencies = true
}

tasks.withType(AbstractModuleCheckTask::class.java)
  .matching { !it.name.endsWith("Auto") }
  .configureEach {
    mustRunAfter(
      tasks.withType(AbstractModuleCheckTask::class.java)
        .matching { it.name.endsWith("Auto") }
    )
  }
