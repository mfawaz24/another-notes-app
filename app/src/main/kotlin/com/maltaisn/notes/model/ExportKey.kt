/*
 * Copyright 2026 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.model

import android.os.Build
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import androidx.annotation.RequiresApi
import java.security.Key
import java.security.KeyStore
import javax.crypto.SecretKey

/**
 * Access to the encrypted export key stored in the Android Keystore.
 *
 * Keystore keys are bound to the device and are never included in backups. After the app's data is
 * restored on a new device, encrypted export can be enabled in preferences without its key existing.
 */
object ExportKey {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "export_key"

    private fun loadKeyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Returns the export key, or `null` if there is none. */
    fun get(): Key? = loadKeyStore().getKey(ALIAS, null)

    /** Whether the export key exists. Always `false` before API 23, which has no keystore support for it. */
    fun exists(): Boolean = Build.VERSION.SDK_INT >= 23 && loadKeyStore().containsAlias(ALIAS)

    @RequiresApi(Build.VERSION_CODES.M)
    fun store(key: SecretKey) {
        loadKeyStore().setEntry(
            ALIAS,
            KeyStore.SecretKeyEntry(key),
            KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
    }

    fun delete() {
        loadKeyStore().deleteEntry(ALIAS)
    }
}

/**
 * Thrown when exporting with encryption enabled but without an export key,
 * typically because the app's data was restored from a backup on another device.
 */
class ExportKeyMissingException : Exception("Encrypted export is enabled but the export key is missing")
