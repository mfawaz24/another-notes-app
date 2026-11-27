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

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportKeyDerivationTest {

    private val salt = ByteArray(16) { it.toByte() }

    @OptIn(ExperimentalStdlibApi::class)
    private fun derive(iterations: Int) =
        ExportKeyDerivation.deriveKey("correct horse", salt, iterations).encoded.toHexString()

    // Expected values computed independently with Python's hashlib.pbkdf2_hmac("sha512", ...).

    @Test
    fun `should derive same key as before for legacy iteration count`() {
        assertEquals("46a6193d6a5bd4250c57d1b04c8af18078e201b9aff362d7358f29dde012e5a4",
            derive(ExportKeyDerivation.LEGACY_ITERATIONS))
    }

    @Test
    fun `should derive key with current iteration count`() {
        assertEquals("978a6aaf4515ab18961276c60c157ffe1cde55c07b660b059ebe59f926582188",
            derive(ExportKeyDerivation.CURRENT_ITERATIONS))
    }

    @Test
    fun `should derive 256-bit AES key`() {
        val key = ExportKeyDerivation.deriveKey("pw", salt, ExportKeyDerivation.ACCEPTED_ITERATIONS.first)
        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size)
    }

    @Test
    fun `should accept iteration counts written by the app`() {
        assertTrue(ExportKeyDerivation.LEGACY_ITERATIONS in ExportKeyDerivation.ACCEPTED_ITERATIONS)
        assertTrue(ExportKeyDerivation.CURRENT_ITERATIONS in ExportKeyDerivation.ACCEPTED_ITERATIONS)
    }

    @Test
    fun `should reject unreasonable iteration counts`() {
        assertFalse(0 in ExportKeyDerivation.ACCEPTED_ITERATIONS)
        assertFalse(-1 in ExportKeyDerivation.ACCEPTED_ITERATIONS)
        assertFalse(Int.MAX_VALUE in ExportKeyDerivation.ACCEPTED_ITERATIONS)
    }
}
