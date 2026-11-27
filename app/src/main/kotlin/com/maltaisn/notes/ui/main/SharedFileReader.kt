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

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import com.maltaisn.notes.TAG
import java.io.IOException
import java.io.Reader

/**
 * Safely reads a text file shared to the app with [android.content.Intent.EXTRA_STREAM].
 *
 * The shared URI comes from an arbitrary app but is opened with this app's own permissions,
 * so it must not be allowed to point to this app's private data (confused deputy).
 * Only `content://` URIs from other apps' providers are accepted, and the amount read is capped.
 */
object SharedFileReader {

    /** Maximum number of characters read from a shared file. Larger files are rejected. */
    const val MAX_CHARS = 1_000_000

    /**
     * Returns whether a shared URI with a [scheme] and [authority] may be opened.
     * `file://` URIs are rejected since they can reference this app's private storage,
     * as are content URIs served by this app's own providers ([ownPackage]).
     */
    fun isAllowedUri(scheme: String?, authority: String?, ownPackage: String): Boolean {
        if (!scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)) {
            return false
        }
        if (authority.isNullOrEmpty()) {
            return false
        }
        // Authorities are case-insensitive in practice, compare accordingly.
        val auth = authority.lowercase()
        val pkg = ownPackage.lowercase()
        return auth != pkg && !auth.startsWith("$pkg.") && !auth.startsWith("$pkg:")
    }

    /**
     * Reads at most [maxChars] characters from [reader].
     * Returns `null` if the content is longer than that, instead of truncating it silently.
     */
    fun readLimited(reader: Reader, maxChars: Int = MAX_CHARS): String? {
        val sb = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val read = reader.read(buffer)
            if (read == -1) break
            if (sb.length + read > maxChars) {
                return null
            }
            sb.appendRange(buffer, 0, read)
        }
        return sb.toString()
    }

    /**
     * Read the text content of a shared [uri], or return `null` if the URI is not allowed,
     * can't be read, or is too large. Returned pair is the file name and the content.
     */
    fun read(contentResolver: ContentResolver, uri: Uri, ownPackage: String): Pair<String, String>? {
        if (!isAllowedUri(uri.scheme, uri.authority, ownPackage)) {
            Log.w(TAG, "Rejected shared URI with scheme '${uri.scheme}' and authority '${uri.authority}'")
            return null
        }
        return try {
            val stream = contentResolver.openInputStream(uri) ?: return null
            val content = stream.reader().use { readLimited(it) } ?: run {
                Log.w(TAG, "Rejected shared file larger than $MAX_CHARS characters")
                return null
            }
            val title = uri.lastPathSegment.orEmpty()
            title to content
        } catch (e: IOException) {
            // File doesn't exist, access error, etc.
            null
        } catch (e: SecurityException) {
            // No permission was granted to read this URI.
            null
        } catch (e: IllegalArgumentException) {
            // Malformed URI or unknown provider.
            null
        }
    }
}
