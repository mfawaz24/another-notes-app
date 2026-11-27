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

package com.maltaisn.notes

import android.app.backup.BackupAgent
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotesBackupAgentTest {

    private val cloud = BackupAgent.FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED
    private val deviceTransfer = BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER

    @Test
    fun `should include data when backup enabled`() {
        assertTrue(NotesBackupAgent.shouldIncludeData(true, 0))
        assertTrue(NotesBackupAgent.shouldIncludeData(true, cloud))
        assertTrue(NotesBackupAgent.shouldIncludeData(true, deviceTransfer))
    }

    @Test
    fun `should not include data in cloud or adb backup when disabled`() {
        assertFalse(NotesBackupAgent.shouldIncludeData(false, 0))
        assertFalse(NotesBackupAgent.shouldIncludeData(false, cloud))
    }

    @Test
    fun `should include data in device transfer even when disabled`() {
        assertTrue(NotesBackupAgent.shouldIncludeData(false, deviceTransfer))
        assertTrue(NotesBackupAgent.shouldIncludeData(false, deviceTransfer or cloud))
    }
}
