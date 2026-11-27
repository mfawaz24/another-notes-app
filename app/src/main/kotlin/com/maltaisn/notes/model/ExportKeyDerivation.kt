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

import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Derivation of the AES key used for encrypted exports, from the user's password.
 */
object ExportKeyDerivation {

    private const val ALGORITHM = "PBKDF2withHmacSHA512"
    private const val KEY_LENGTH = 256

    /** Iteration count used for new keys (OWASP recommendation for PBKDF2-HMAC-SHA512). */
    const val CURRENT_ITERATIONS = 210_000

    /**
     * Iteration count used before it was stored in exported files and preferences.
     * Files without an iteration count and keys created before it was stored use this value.
     */
    const val LEGACY_ITERATIONS = 120_000

    /**
     * Range of iteration counts accepted from an imported file. The upper bound prevents
     * a crafted file from making key derivation run for an unreasonable amount of time.
     */
    val ACCEPTED_ITERATIONS = 10_000..10_000_000

    /**
     * Derive the key from a [password] and [salt]. The intermediate copies of the
     * password and key material are cleared once the key is derived. Note that [password]
     * itself can't be cleared since strings are immutable.
     */
    fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKey {
        val passwordChars = password.toCharArray()
        val keySpec = PBEKeySpec(passwordChars, salt, iterations, KEY_LENGTH)
        passwordChars.fill('\u0000')  // PBEKeySpec keeps its own copy
        try {
            val secretKey = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(keySpec)
            val encoded = secretKey.encoded
            try {
                return SecretKeySpec(encoded, "AES")  // SecretKeySpec keeps its own copy
            } finally {
                encoded.fill(0)
            }
        } finally {
            keySpec.clearPassword()
        }
    }
}
