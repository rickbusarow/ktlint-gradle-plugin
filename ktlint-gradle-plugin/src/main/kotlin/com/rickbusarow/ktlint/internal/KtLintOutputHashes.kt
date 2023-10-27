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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

internal class KtLintOutputHashes(
  private val delegate: Map<File, String>,
  val changedFilesRelative: List<File>
) : Serializable {

  val entries: Set<Map.Entry<File, String>>
    get() = delegate.entries
  val filesRelative: Set<File>
    get() = delegate.keys

  operator fun get(relativeFile: File): String? = delegate[relativeFile]

  fun writeTo(file: File) {
    file.writeSerializable(this@KtLintOutputHashes)
  }

  companion object {

    fun File.readFormatOutput(): KtLintOutputHashes = readSerializable()
  }
}

internal class KtLintInputHashes(
  private val delegate: Map<File, String>
) : Serializable {

  val entries: Set<Map.Entry<File, String>>
    get() = delegate.entries
  val filesRelative: Set<File>
    get() = delegate.keys

  operator fun get(relativeFile: File): String? = delegate[relativeFile]

  fun writeTo(file: File) {
    file.writeSerializable(this@KtLintInputHashes)
  }

  companion object {
    fun File.readKtLintInputHashes(): KtLintInputHashes = readSerializable()
  }
}

internal inline fun <reified T : Serializable> File.readSerializable(): T {
  return ObjectInputStream(
    ByteArrayInputStream(
      FileInputStream(this)
        .use { fis -> fis.readBytes() }
    )
  )
    .readObject() as T
}

internal inline fun <reified T : Serializable> File.writeSerializable(t: T) {

  val newBytes = ByteArrayOutputStream().use { byteStream ->
    ObjectOutputStream(byteStream).use { objectStream ->
      objectStream.writeObject(t)
    }
    byteStream.toByteArray()
  }

  makeParentDir().writeBytes(newBytes)
}
