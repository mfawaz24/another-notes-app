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

package com.maltaisn.notes.ui.main

import org.junit.Test
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedFileReaderTest {

    private val pkg = "com.maltaisn.notes.sync"

    @Test
    fun `should allow content uri from other app`() {
        assertTrue(SharedFileReader.isAllowedUri("content", "com.android.providers.downloads.documents", pkg))
        assertTrue(SharedFileReader.isAllowedUri("CONTENT", "com.example.fileprovider", pkg))
    }

    @Test
    fun `should reject file uri`() {
        assertFalse(SharedFileReader.isAllowedUri("file", null, pkg))
        assertFalse(SharedFileReader.isAllowedUri("file", "", pkg))
    }

    @Test
    fun `should reject other or missing schemes`() {
        assertFalse(SharedFileReader.isAllowedUri(null, "com.example.provider", pkg))
        assertFalse(SharedFileReader.isAllowedUri("android.resource", pkg, pkg))
        assertFalse(SharedFileReader.isAllowedUri("http", "example.com", pkg))
    }

    @Test
    fun `should reject content uri without authority`() {
        assertFalse(SharedFileReader.isAllowedUri("content", null, pkg))
        assertFalse(SharedFileReader.isAllowedUri("content", "", pkg))
    }

    @Test
    fun `should reject own providers`() {
        assertFalse(SharedFileReader.isAllowedUri("content", pkg, pkg))
        assertFalse(SharedFileReader.isAllowedUri("content", "$pkg.androidx-startup", pkg))
        assertFalse(SharedFileReader.isAllowedUri("content", "$pkg.fileprovider", pkg))
        assertFalse(SharedFileReader.isAllowedUri("content", "COM.MALTAISN.NOTES.SYNC.provider", pkg))
    }

    @Test
    fun `should allow authority that only shares a prefix with own package`() {
        assertTrue(SharedFileReader.isAllowedUri("content", "${pkg}evil.provider", pkg))
    }

    @Test
    fun `should read content under limit`() {
        assertEquals("hello", SharedFileReader.readLimited(StringReader("hello"), 10))
        assertEquals("", SharedFileReader.readLimited(StringReader(""), 10))
    }

    @Test
    fun `should read content exactly at limit`() {
        val text = "a".repeat(20_000)
        assertEquals(text, SharedFileReader.readLimited(StringReader(text), 20_000))
    }

    @Test
    fun `should reject content over limit`() {
        assertNull(SharedFileReader.readLimited(StringReader("a".repeat(20_001)), 20_000))
    }
}
