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

/** shorthand for `mapTo(mutableSetOf()) { ... }` */
inline fun <C : Iterable<T>, T, R> C.mapToSet(
  destination: MutableSet<R> = mutableSetOf(),
  transform: (T) -> R
): MutableSet<R> = mapTo(destination, transform)

/** shorthand for `flatMapTo(mutableSetOf()) { ... }` */
inline fun <T, R> Iterable<T>.flatMapToSet(
  destination: MutableSet<R> = mutableSetOf(),
  transform: (T) -> Iterable<R>
): MutableSet<R> = flatMapTo(destination, transform)
