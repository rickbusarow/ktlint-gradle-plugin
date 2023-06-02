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

package com.rickbusarow.ktlint.testing

/**
 * Returns the current class if it's a real class, otherwise walks up
 * the hierarchy of enclosing/nesting classes until it finds a real one.
 *
 * In practical terms, this strips away Kotlin's anonymous lambda
 * "classes" and other compatibility shims, returning the real class.
 */
tailrec fun Class<*>.firstNonSyntheticClass(): Class<*> {
  return when {
    canonicalName != null -> this
    else -> enclosingClass.firstNonSyntheticClass()
  }
}
