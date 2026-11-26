package com.example.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Lifecycle states for automatic Settings persistence confirmation.
 *
 * INVARIANT: [SAVED] is NEVER emitted until the underlying storage write has actually completed
 * without throwing an exception.
 */
enum class SettingSavePhase {
    IDLE,
    SAVING,
    SAVED,
    FAILED
}

data class SettingSaveEntry(
    val settingKey: String,
    val sectionKey: String,
    val phase: SettingSavePhase = SettingSavePhase.IDLE,
    val revision: Long = 0L,
    val errorMessage: String? = null
)

/**
 * Coordinates automatic preference saving across all CTECS Settings controls:
 * - Debounces rapid repeated edits (e.g., sliders/text inputs) while preserving the latest value.
 * - Skips redundant writes and avoids spurious re-animations when the value signature is unchanged.
 * - Prevents race conditions and overlapping confirmations across rapidly changed settings.
 * - Emits SAVING -> SAVED (auto-hides after ~1.3s) ONLY after real storage persistence succeeds.
 * - On storage failure, retains the user's latest write action in memory and exposes safe retry.
 */
class SettingsAutoSaveCoordinator(
    private val scope: CoroutineScope,
    private val savedVisibleDurationMs: Long = DEFAULT_SAVED_VISIBLE_MS
) {
    private val revisionCounter = AtomicLong(0L)
    private val lastCommittedSignatures = ConcurrentHashMap<String, Int>()
    private val pendingJobs = ConcurrentHashMap<String, Job>()
    private val hideJobs = ConcurrentHashMap<String, Job>()
    private val pendingRetries = ConcurrentHashMap<String, PendingWriteRequest>()

    private val _entries = MutableStateFlow<Map<String, SettingSaveEntry>>(emptyMap())
    val entries: StateFlow<Map<String, SettingSaveEntry>> = _entries.asStateFlow()

    private val _sectionEntries = MutableStateFlow<Map<String, SettingSaveEntry>>(emptyMap())
    val sectionEntries: StateFlow<Map<String, SettingSaveEntry>> = _sectionEntries.asStateFlow()

    private val activeControlScope = ThreadLocal<ControlActionContext?>()

    private data class ControlActionContext(
        val controlTag: String,
        val sectionKeyOverride: String?,
        val debounceMs: Long
    )

    private data class PendingWriteRequest(
        val canonicalKey: String,
        val settingKey: String,
        val sectionKey: String,
        val valueSignature: Int,
        val writeBlock: suspend () -> Unit
    )

    /**
     * Scopes a UI control interaction (switch toggle, slider drag, chip tap) so the tiny
     * confirmation indicator renders directly beside that exact control without leaking onto
     * sibling controls sharing the same underlying settings object.
     */
    fun <T> withControlAction(
        controlTag: String,
        sectionKeyOverride: String? = null,
        debounceMs: Long = 0L,
        block: () -> T
    ): T {
        val previous = activeControlScope.get()
        activeControlScope.set(
            ControlActionContext(
                controlTag = controlTag,
                sectionKeyOverride = sectionKeyOverride?.takeIf { it.isNotBlank() },
                debounceMs = debounceMs
            )
        )
        return try {
            block()
        } finally {
            activeControlScope.set(previous)
        }
    }

    /**
     * Schedules an automatic save for [settingKey].
     *
     * @param settingKey Canonical identifier of the preference group being persisted.
     * @param sectionKey Settings category/section key so the section header can reflect active saves.
     * @param valueSignature Content hash of the target preference value used to avoid duplicate writes.
     * @param debounceMs Optional debounce window for rapid text/slider edits (0 for instant toggles/chips).
     * @param writeBlock Suspending storage persistence operation that must complete before SAVED is shown.
     */
    fun saveSetting(
        settingKey: String,
        sectionKey: String,
        valueSignature: Int,
        debounceMs: Long = 0L,
        writeBlock: suspend () -> Unit
    ) {
        val controlCtx = activeControlScope.get()
        val displayKey = controlCtx?.controlTag?.takeIf { it.isNotBlank() } ?: settingKey
        val effectiveSectionKey = controlCtx?.sectionKeyOverride ?: sectionKey
        val effectiveDebounceMs = maxOf(debounceMs, controlCtx?.debounceMs ?: 0L)

        val currentEntry = _entries.value[displayKey] ?: _entries.value[settingKey]
        val previousCommitted = lastCommittedSignatures[settingKey]
        if (previousCommitted != null && previousCommitted == valueSignature && currentEntry?.phase != SettingSavePhase.FAILED) {
            return
        }

        val request = PendingWriteRequest(
            canonicalKey = settingKey,
            settingKey = displayKey,
            sectionKey = effectiveSectionKey,
            valueSignature = valueSignature,
            writeBlock = writeBlock
        )
        pendingRetries[displayKey] = request
        if (displayKey != settingKey) {
            pendingRetries[settingKey] = request
        }

        // Cancel any superseded debounced write or pending auto-hide timer for this canonical setting
        pendingJobs.remove(settingKey)?.cancel()
        hideJobs.remove(displayKey)?.cancel()
        if (displayKey != settingKey) {
            hideJobs.remove(settingKey)?.cancel()
        }

        val rev = revisionCounter.incrementAndGet()
        val job = scope.launch {
            if (effectiveDebounceMs > 0L) {
                delay(effectiveDebounceMs)
            }
            executeWrite(request, rev)
        }
        pendingJobs[settingKey] = job
    }

    /**
     * Retries a failed save using the latest retained in-memory preference value.
     */
    fun retrySave(settingKey: String) {
        val request = pendingRetries[settingKey] ?: return
        pendingJobs.remove(request.canonicalKey)?.cancel()
        hideJobs.remove(request.settingKey)?.cancel()
        if (request.settingKey != request.canonicalKey) {
            hideJobs.remove(request.canonicalKey)?.cancel()
        }

        val rev = revisionCounter.incrementAndGet()
        val job = scope.launch {
            executeWrite(request, rev)
        }
        pendingJobs[request.canonicalKey] = job
    }

    /**
     * Seeds known persisted signatures so initial load/recomposition never triggers false save animations.
     */
    fun seedPersistedSignature(settingKey: String, valueSignature: Int) {
        lastCommittedSignatures.putIfAbsent(settingKey, valueSignature)
    }

    fun getEntry(settingKey: String): SettingSaveEntry? = _entries.value[settingKey]

    private suspend fun executeWrite(request: PendingWriteRequest, rev: Long) {
        updateEntry(
            SettingSaveEntry(
                settingKey = request.settingKey,
                sectionKey = request.sectionKey,
                phase = SettingSavePhase.SAVING,
                revision = rev,
                errorMessage = null
            )
        )

        try {
            request.writeBlock()
            // Only emit SAVED if this write is still the latest revision for this setting
            val active = _entries.value[request.settingKey]
            if (active != null && active.revision != rev) {
                return
            }
            lastCommittedSignatures[request.canonicalKey] = request.valueSignature
            pendingRetries.remove(request.settingKey)
            pendingRetries.remove(request.canonicalKey)

            updateEntry(
                SettingSaveEntry(
                    settingKey = request.settingKey,
                    sectionKey = request.sectionKey,
                    phase = SettingSavePhase.SAVED,
                    revision = rev,
                    errorMessage = null
                )
            )
            scheduleAutoHide(request.settingKey, request.sectionKey, rev)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            val active = _entries.value[request.settingKey]
            if (active != null && active.revision != rev) {
                return
            }
            updateEntry(
                SettingSaveEntry(
                    settingKey = request.settingKey,
                    sectionKey = request.sectionKey,
                    phase = SettingSavePhase.FAILED,
                    revision = rev,
                    errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "Could not save"
                )
            )
        }
    }

    private fun scheduleAutoHide(settingKey: String, sectionKey: String, rev: Long) {
        hideJobs.remove(settingKey)?.cancel()
        hideJobs[settingKey] = scope.launch {
            delay(savedVisibleDurationMs)
            val current = _entries.value[settingKey]
            if (current != null && current.revision == rev && current.phase == SettingSavePhase.SAVED) {
                updateEntry(
                    current.copy(phase = SettingSavePhase.IDLE)
                )
            }
            val currentSection = _sectionEntries.value[sectionKey]
            if (currentSection != null && currentSection.settingKey == settingKey && currentSection.revision == rev && currentSection.phase == SettingSavePhase.SAVED) {
                _sectionEntries.update { map ->
                    map + (sectionKey to currentSection.copy(phase = SettingSavePhase.IDLE))
                }
            }
        }
    }

    private fun updateEntry(entry: SettingSaveEntry) {
        _entries.update { map -> map + (entry.settingKey to entry) }
        _sectionEntries.update { map ->
            val existing = map[entry.sectionKey]
            if (existing == null || entry.revision >= existing.revision || entry.phase == SettingSavePhase.FAILED) {
                map + (entry.sectionKey to entry)
            } else {
                map
            }
        }
    }

    companion object {
        const val DEFAULT_SAVED_VISIBLE_MS: Long = 1300L
        const val CONTINUOUS_INPUT_DEBOUNCE_MS: Long = 220L
    }
}
