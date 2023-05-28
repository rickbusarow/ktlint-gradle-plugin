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

import org.gradle.api.Action
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/** code golf for `matching { it.name == taskName }` */
internal fun TaskContainer.matchingName(
  taskName: String
): TaskCollection<Task> = matching { it.name == taskName }

/**
 * adds all [objects] as dependencies to every task in the collection, inside a `configureEach { }`
 */
internal fun <T : Task> TaskCollection<T>.dependOn(vararg objects: Any): TaskCollection<T> {
  return also { taskCollection ->
    taskCollection.configureEach { task -> task.dependsOn(*objects) }
  }
}

/** adds all [objects] as dependencies inside a configuration block, inside a `configure { }` */
internal fun <T : Task> TaskProvider<T>.dependsOn(vararg objects: Any): TaskProvider<T> {
  return also { provider ->
    provider.configure { task ->
      task.dependsOn(*objects)
    }
  }
}

/** makes the receiver task a dependency of the [dependentTask] parameter. */
internal fun <T : Task> TaskProvider<T>.addAsDependencyTo(dependentTask: TaskProvider<*>): TaskProvider<T> {
  return also { receiver ->
    dependentTask.dependsOn(receiver)
  }
}

/** makes the receiver task a dependency of the tasks in the [dependentTasks] collection. */
internal fun <T : Task> TaskProvider<T>.addAsDependencyTo(dependentTasks: TaskCollection<*>): TaskProvider<T> {
  return also { receiver ->
    dependentTasks.dependOn(receiver)
  }
}

/**
 * Returns a collection containing the objects in this collection of the
 * given type. Equivalent to calling `withType(type).all(configureAction)`.
 *
 * @param configuration The action to execute for each object in the resulting collection.
 * @return The matching objects. Returns an empty collection
 *   if there are no such objects in this collection.
 * @see DomainObjectCollection.withType
 */
internal inline fun <reified S : Any> DomainObjectCollection<in S>.withType(
  noinline configuration: (S) -> Unit
): DomainObjectCollection<S>? = withType(S::class.java, configuration)

/**
 * Returns a collection containing the objects in this collection of the given
 * type. The returned collection is live, so that when matching objects are later
 * added to this collection, they are also visible in the filtered collection.
 *
 * @return The matching objects. Returns an empty collection
 *   if there are no such objects in this collection.
 * @see DomainObjectCollection.withType
 */
internal inline fun <reified S : Any> DomainObjectCollection<in S>.withType(): DomainObjectCollection<S> =
  withType(S::class.java)

/**
 * Registers a task with the specified name, type, and configuration action in the task container. If a task with the same name already exists, the configuration action is applied to the existing task. If the task doesn't exist, a new task is registered with the provided configuration action.
 *
 * @param name The name of the task to register.
 * @param type The class object representing the type of task.
 * @param configurationAction The action that configures the task.
 * @return The task provider for the registered task.
 */
internal fun <T : Task> TaskContainer.registerOnce(
  name: String,
  type: Class<T>,
  configurationAction: Action<in T>
): TaskProvider<T> = if (names.contains(name)) {
  named(name, type, configurationAction)
} else {
  register(name, type, configurationAction)
}
