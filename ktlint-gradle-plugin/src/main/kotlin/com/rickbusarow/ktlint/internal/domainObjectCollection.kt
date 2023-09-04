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
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.internal.DefaultNamedDomainObjectCollection
import org.gradle.api.internal.DefaultNamedDomainObjectCollection.ElementInfo

/**
 * Executes a given [Action] when an element is registered in the collection, without triggering its creation or configuration.  The given [configurationAction] is executed against the object before it is returned from the provider.
 *
 * @param configurationAction The [Action] to execute on the newly registered element.
 * @receiver [NamedDomainObjectCollection] where the element is being registered.
 * @throws IllegalArgumentException If the receiver is not a [DefaultNamedDomainObjectCollection].
 */
internal inline fun <reified T> NamedDomainObjectCollection<T>.whenElementRegistered(
  configurationAction: Action<T>
) {
  requireDefaultCollection<T>()
    .whenElementKnown { named(it.name, configurationAction) }
}

@PublishedApi
internal inline fun <reified T> NamedDomainObjectCollection<T>.requireDefaultCollection(): DefaultNamedDomainObjectCollection<T> {
  require(this is DefaultNamedDomainObjectCollection<T>) {
    "The receiver collection must extend " +
      "${DefaultNamedDomainObjectCollection::class.qualifiedName}, " +
      "but this type is ${this::class.java.canonicalName}."
  }
  return this@requireDefaultCollection
}

@PublishedApi
internal inline fun <reified T> NamedDomainObjectCollection<T>.whenElementKnown(
  action: Action<ElementInfo<T>>
) {
  requireDefaultCollection().whenElementKnown(action)
}

/**
 * Executes a given [Action] when an element with a specific name is registered in the collection, without triggering its creation or configuration.  The given [configurationAction] is executed against the object before it is returned from the provider.
 *
 * @param name The name of the element to observe.
 * @param configurationAction The [Action] to execute on the newly registered element.
 * @receiver [NamedDomainObjectCollection] where the element is being registered.
 * @throws IllegalArgumentException If the receiver is not a [DefaultNamedDomainObjectCollection].
 */
internal inline fun <reified T> NamedDomainObjectCollection<T>.whenElementRegistered(
  name: String,
  configurationAction: Action<T>
) {
  whenElementKnown { if (it.name == name) named(name, configurationAction) }
}

/**
 * Executes a given [Action] when an element with a specific name and type is registered in the collection, without triggering its creation or configuration.  The given [configurationAction] is executed against the object before it is returned from the provider.
 *
 * @param name The name of the element to observe.
 * @param configurationAction The [Action] to execute on the newly registered element.
 * @receiver [NamedDomainObjectCollection] where the element is being registered.
 * @throws IllegalArgumentException If the receiver is not a [DefaultNamedDomainObjectCollection].
 */
@JvmName("whenElementRegisteredTyped")
internal inline fun <reified T, reified R : T> NamedDomainObjectCollection<T>.whenElementRegistered(
  name: String,
  configurationAction: Action<R>
) {
  requireDefaultCollection<T>()
    .whenElementKnown { elementInfo ->
      if (elementInfo.name == name) {

        named(name, R::class.java, configurationAction)
      }
    }
}
