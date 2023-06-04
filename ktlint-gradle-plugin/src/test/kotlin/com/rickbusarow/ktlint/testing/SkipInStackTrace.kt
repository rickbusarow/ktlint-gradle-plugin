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

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Method
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER

/**
 * Indicates that the annotated function/property should be ignored when walking a
 * stack trace, such as in assertions or when trying to parse a test function's name.
 *
 * @see StackTraceElement.isSkipped
 * @since 0.1.1
 */
@Target(
  FUNCTION,
  PROPERTY,
  PROPERTY_GETTER,
  PROPERTY_SETTER,
  CLASS
)
@Retention(RUNTIME)
annotation class SkipInStackTrace

@PublishedApi
internal fun AnnotatedElement.hasSkipAnnotation(): Boolean {
  return isAnnotationPresent(SkipInStackTrace::class.java)
}

private val sdkPackagePrefixes = setOf("java", "jdk", "kotlin")

@SkipInStackTrace
@PublishedApi
internal fun StackTraceElement.isSkipped(): Boolean {

  val clazz = declaringClass()

  val enclosingClasses = generateSequence(clazz) { c -> c.enclosingClass }

  if (enclosingClasses.any { it.hasSkipAnnotation() }) return true

  val packageRoot = clazz.canonicalName
    ?.split('.')
    ?.firstOrNull()
    ?: return true

  if (packageRoot in sdkPackagePrefixes) {
    return true
  }

  return clazz
    .methods
    .filter { it.name == methodName.removeSuffix("\$default") }
    .requireAllOrNoneAreAnnotated()
}

@SkipInStackTrace
@PublishedApi
internal fun StackTraceElement.declaringClass(): Class<*> {

  return Class.forName(className)
}

@SkipInStackTrace
private fun List<Method>.requireAllOrNoneAreAnnotated(): Boolean {

  val (annotated, notAnnotated) = partition {
    it.hasSkipAnnotation()
  }

  require(annotated.size == size || notAnnotated.size == size) {
    "The function named '${first().name}' is overloaded, " +
      "and only some those overloads are annotated with `@SkipInStackTrace`.  " +
      "Either all must be annotated or none of them."
  }

  return annotated.size == size
}
