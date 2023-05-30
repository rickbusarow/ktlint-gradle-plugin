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

package com.rickbusarow.ktlint.internal

internal typealias GradleProject = org.gradle.api.Project
internal typealias GradleConfiguration = org.gradle.api.artifacts.Configuration
internal typealias GradleLogger = org.gradle.api.logging.Logger
internal typealias GradleLogging = org.gradle.api.logging.Logging
internal typealias GradleProperty<T> = org.gradle.api.provider.Property<T>
internal typealias GradleProvider<T> = org.gradle.api.provider.Provider<T>
