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

import android.annotation.SuppressLint
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.preference.PreferenceManager
import com.maltaisn.notes.model.DefaultPrefsManager

/**
 * Backup agent used to let the user opt out of Android's backup of the app data.
 *
 * Android Auto Backup is enabled by the `allowBackup` manifest attribute, which can't change at
 * runtime. When the user turns off [DefaultPrefsManager.SYSTEM_BACKUP], no files are included in
 * cloud or `adb` backups. Device-to-device transfers still include everything, since those
 * are started by the user to move to a new device.
 *
 * This may run with the app in restricted mode, without dependency injection,
 * so the preference is read directly.
 */
class NotesBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        val enabled = PreferenceManager.getDefaultSharedPreferences(this)
            .getBoolean(DefaultPrefsManager.SYSTEM_BACKUP, DefaultPrefsManager.SYSTEM_BACKUP_DEFAULT)
        val transportFlags = if (Build.VERSION.SDK_INT >= 28) data.transportFlags else 0
        if (shouldIncludeData(enabled, transportFlags)) {
            // Default behavior: back up all files, as Auto Backup would without an agent.
            super.onFullBackup(data)
        } else {
            Log.i(TAG, "Backup of app data is disabled by the user, nothing included")
        }
    }

    // Key-value backup isn't used, only full backup (fullBackupOnly="true" in manifest).
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) = Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    companion object {
        /**
         * Whether to include the app data in a backup, depending on whether the user [enabled] it,
         * and on the backup's [transportFlags].
         */
        @SuppressLint("InlinedApi")  // Only the flag value is used. Below API 28, transportFlags is 0.
        fun shouldIncludeData(enabled: Boolean, transportFlags: Int): Boolean =
            enabled || (transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
    }
}
