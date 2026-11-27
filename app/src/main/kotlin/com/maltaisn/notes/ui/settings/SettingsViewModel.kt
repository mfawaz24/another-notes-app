/*
 * Copyright 2025 Nicolas Maltais
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

package com.maltaisn.notes.ui.settings

import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maltaisn.notes.R
import com.maltaisn.notes.model.ArchiveExporter
import com.maltaisn.notes.model.ExportKey
import com.maltaisn.notes.model.ExportKeyDerivation
import com.maltaisn.notes.model.ExportKeyMissingException
import com.maltaisn.notes.model.JsonManager
import com.maltaisn.notes.model.JsonManager.ImportResult
import com.maltaisn.notes.model.LabelsRepository
import com.maltaisn.notes.model.NotesRepository
import com.maltaisn.notes.model.PrefsManager
import com.maltaisn.notes.model.ReminderAlarmManager
import com.maltaisn.notes.ui.Event
import com.maltaisn.notes.ui.send
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val notesRepository: NotesRepository,
    private val labelsRepository: LabelsRepository,
    private val prefsManager: PrefsManager,
    private val jsonManager: JsonManager,
    private val archiveExporter: ArchiveExporter,
    private val reminderAlarmManager: ReminderAlarmManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _messageEvent = MutableLiveData<Event<Int>>()
    val messageEvent: LiveData<Event<Int>>
        get() = _messageEvent

    private val _lastAutoExport = MutableLiveData<Long>()
    val lastAutoExport: LiveData<Long>
        get() = _lastAutoExport

    private val _exportData = MutableLiveData<Event<String>>()
    val exportData: LiveData<Event<String>>
        get() = _exportData

    private val _releasePersistableUriEvent = MutableLiveData<Event<String>>()
    val releasePersistableUriEvent: LiveData<Event<String>>
        get() = _releasePersistableUriEvent

    private val _showImportPasswordDialogEvent = MutableLiveData<Event<Unit>>()
    val showImportPasswordDialogEvent: LiveData<Event<Unit>>
        get() = _showImportPasswordDialogEvent

    private val _showExportKeyMissingDialogEvent = MutableLiveData<Event<Unit>>()
    val showExportKeyMissingDialogEvent: LiveData<Event<Unit>>
        get() = _showExportKeyMissingDialogEvent

    private val _askNotificationPermission = MutableLiveData<Event<Unit>>()
    val askNotificationPermission: LiveData<Event<Unit>>
        get() = _askNotificationPermission

    private val _askReminderPermission = MutableLiveData<Event<Unit>>()
    val askReminderPermission: LiveData<Event<Unit>>
        get() = _askReminderPermission

    private var importJsonData = savedStateHandle[KEY_IMPORTED_JSON_DATA] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_IMPORTED_JSON_DATA] = value
        }

    private var exportType = savedStateHandle[KEY_EXPORT_TYPE] ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_EXPORT_TYPE] = value
        }

    /**
     * Whether the export password dialog is shown to replace a missing export key,
     * rather than to enable encryption. Cancelling it then leaves encryption enabled.
     */
    var isReplacingMissingExportKey: Boolean = savedStateHandle[KEY_REPLACING_MISSING_EXPORT_KEY] ?: false
        set(value) {
            field = value
            savedStateHandle[KEY_REPLACING_MISSING_EXPORT_KEY] = value
        }

    init {
        _lastAutoExport.value = prefsManager.lastAutoExportTime

        // Check once per screen, not again when it's recreated with the dialog already shown.
        if (KEY_EXPORT_KEY_CHECKED !in savedStateHandle) {
            savedStateHandle[KEY_EXPORT_KEY_CHECKED] = true
            viewModelScope.launch(Dispatchers.IO) {
                checkExportKey()
            }
        }
    }

    /**
     * If encrypted export is enabled but its key is missing, ask the user to set the password again.
     * This happens after the app's data is restored from a backup, since keystore keys aren't backed up.
     * Returns whether the key is missing.
     */
    private fun checkExportKey(): Boolean {
        // Encrypted export isn't available before API 26.
        if (Build.VERSION.SDK_INT < 26 || !prefsManager.shouldEncryptExportedData || ExportKey.exists()) {
            return false
        }
        _showExportKeyMissingDialogEvent.postValue(Event(Unit))
        return true
    }

    private suspend fun tryExport(action: suspend () -> Unit) {
        try {
            action()
            showMessage(R.string.export_success)
        } catch (_: Exception) {
            showMessage(R.string.export_fail)
        }
    }

    private suspend fun exportZipData(output: OutputStream, untitledName: String) {
        tryExport {
            archiveExporter.exportArchive(output, untitledName)
        }
    }

    private suspend fun exportJsonData(output: OutputStream) {
        val jsonData = try {
            jsonManager.exportJsonData()
        } catch (_: ExportKeyMissingException) {
            checkExportKey()
            output.close()
            return
        } catch (_: Exception) {
            showMessage(R.string.export_serialization_fail)
            return
        }

        tryExport {
            output.use {
                // bufferedWriter().write fails here for some reason...
                output.write(jsonData.toByteArray())
            }
        }
    }

    fun exportData(output: OutputStream, untitledName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (exportType) {
                EXPORT_TYPE_JSON -> exportJsonData(output)
                EXPORT_TYPE_ZIP -> exportZipData(output, untitledName)
            }
        }
    }

    fun onExportJsonRequested() {
        exportType = EXPORT_TYPE_JSON
        _exportData.send(exportType)
    }

    fun onExportZipRequested() {
        exportType = EXPORT_TYPE_ZIP
        _exportData.send(exportType)
    }

    fun setupAutoExport(output: OutputStream, uri: String) {
        prefsManager.autoExportUri = uri
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = try {
                jsonManager.exportJsonData()
            } catch (_: ExportKeyMissingException) {
                checkExportKey()
                return@launch
            } catch (_: Exception) {
                showMessage(R.string.export_serialization_fail)
                return@launch
            }

            try {
                output.use {
                    output.write(jsonData.toByteArray())
                }
                showMessage(R.string.export_success)

                val now = System.currentTimeMillis()
                prefsManager.lastAutoExportTime = now
                _lastAutoExport.postValue(now)
            } catch (_: Exception) {
                showMessage(R.string.export_fail)
            }
        }
    }

    fun disableAutoExport() {
        prefsManager.disableAutoExport()
        _releasePersistableUriEvent.send(prefsManager.autoExportUri)
    }

    fun importData(input: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            val jsonData = try {
                input.bufferedReader().readText()
            } catch (_: Exception) {
                showMessage(R.string.import_bad_input)
                return@launch
            }
            val result = jsonManager.importJsonData(jsonData)
            if (result == ImportResult.KEY_MISSING_OR_INCORRECT && Build.VERSION.SDK_INT >= 26) {
                // Show dialog for user to enter encryption password
                _showImportPasswordDialogEvent.postValue(Event(Unit))
                importJsonData = jsonData
            } else {
                // Show result in case the imported file was not encrypted
                showImportResultMessage(result)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun importSavedEncryptedJsonData(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val salt = Base64.decode(prefsManager.encryptedImportKeyDerivationSalt, BASE64_FLAGS)
            val decryptionKey = ExportKeyDerivation.deriveKey(password, salt,
                prefsManager.encryptedImportKeyIterations)

            val result = jsonManager.importJsonData(importJsonData, decryptionKey)
            importJsonData = ""
            showImportResultMessage(result)
        }
    }

    private fun showImportResultMessage(result: ImportResult) {
        showMessage(when (result) {
            ImportResult.BAD_FORMAT -> R.string.import_bad_format
            ImportResult.BAD_DATA -> R.string.import_bad_data
            ImportResult.FUTURE_VERSION -> R.string.import_future_version
            ImportResult.KEY_MISSING_OR_INCORRECT -> {
                if (Build.VERSION.SDK_INT >= 26) {
                    R.string.encrypted_import_key_error
                } else {
                    R.string.encrypted_import_encryption_unsupported
                }
            }

            ImportResult.SUCCESS -> R.string.import_success
        })

        viewModelScope.launch {
            if ((result == ImportResult.SUCCESS || result == ImportResult.FUTURE_VERSION) &&
                notesRepository.getNotesWithReminder().firstOrNull() != null
            ) {
                _askNotificationPermission.send()
                _askReminderPermission.send()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun generateExportKeyFromPassword(password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Generate salt
            val secureRandom = SecureRandom()
            val salt = ByteArray(128)
            secureRandom.nextBytes(salt)
            // Save salt for later use
            prefsManager.encryptedExportKeyDerivationSalt = Base64.encodeToString(salt, BASE64_FLAGS)

            // Derive key
            val iterations = ExportKeyDerivation.CURRENT_ITERATIONS
            val aesKey = ExportKeyDerivation.deriveKey(password, salt, iterations)

            // Store key in Android KeyStore
            ExportKey.store(aesKey)
            // Save iteration count only once the key it applies to is stored.
            prefsManager.encryptedExportKeyIterations = iterations
            onExportKeyMissingResolved()
            showMessage(R.string.encrypted_export_key_generation_successful)
        }
    }

    fun deleteExportKey() {
        viewModelScope.launch(Dispatchers.IO) {
            ExportKey.delete()
            onExportKeyMissingResolved()
            showMessage(R.string.encrypted_export_key_deletion_successful)
        }
    }

    /**
     * Called when a new export key is set or encryption is disabled. If the last export failed because the key
     * was missing, clear that failure since its cause is resolved.
     */
    private fun onExportKeyMissingResolved() {
        if (prefsManager.exportKeyMissing) {
            prefsManager.exportKeyMissing = false
            prefsManager.autoExportFailed = false
        }
    }

    fun clearData() {
        viewModelScope.launch {
            notesRepository.clearAllData()
            labelsRepository.clearAllData()
            reminderAlarmManager.removeAllAlarms()
            showMessage(R.string.pref_data_clear_success_message)
        }
    }

    private fun showMessage(messageId: Int) {
        _messageEvent.postValue(Event(messageId))
    }

    companion object {
        private const val KEY_IMPORTED_JSON_DATA = "importedJsonData"
        private const val KEY_EXPORT_TYPE = "exportType"
        private const val KEY_EXPORT_KEY_CHECKED = "exportKeyChecked"
        private const val KEY_REPLACING_MISSING_EXPORT_KEY = "replacingMissingExportKey"

        private const val EXPORT_TYPE_JSON = "json"
        private const val EXPORT_TYPE_ZIP = "zip"

        private const val BASE64_FLAGS = Base64.NO_WRAP or Base64.NO_PADDING
    }
}
