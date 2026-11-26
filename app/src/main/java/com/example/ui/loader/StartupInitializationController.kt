package com.example.ui.loader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Measurable startup initialisation phase with real completion weights.
 */
data class StartupTask(
    val id: String,
    val statusLabel: String,
    val weight: Float,
    val isEssential: Boolean = true,
    val block: suspend () -> Unit
)

data class StartupLoaderUiState(
    val isVisible: Boolean = true,
    val progress: Float = 0f,
    val isIndeterminate: Boolean = false,
    val statusMessage: String = "Initialising CTECS...",
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
) {
    /**
     * Never reports 100% until [isCompleted] is genuinely true after all essential tasks succeed.
     */
    val displayPercentage: Int
        get() = if (isCompleted) 100 else (progress.coerceIn(0f, 0.99f) * 100f).toInt()
}

/**
 * Tracks real CTECS application startup tasks without fabricating progress.
 *
 * Guarantees:
 * - Runs once per ViewModel/process lifecycle (never restarts on navigation or activity recreation).
 * - Updates progress strictly from completed real task weights.
 * - Never reaches 100% until every essential startup task completes without error.
 * - Handles essential task failures safely with retry while preserving already loaded preferences.
 * - Non-essential tasks never block startup or crash the application.
 */
class StartupInitializationController(
    private val scope: CoroutineScope,
    private val completionTransitionDelayMs: Long = 220L
) {
    private val _uiState = MutableStateFlow(StartupLoaderUiState())
    val uiState: StateFlow<StartupLoaderUiState> = _uiState.asStateFlow()

    private val completedTaskIds = linkedSetOf<String>()
    private var registeredTasks: List<StartupTask> = emptyList()
    private var activeJob: Job? = null

    @Volatile
    var hasStarted: Boolean = false
        private set

    @Volatile
    var hasFinishedSuccessfully: Boolean = false
        private set

    fun startIfNeeded(tasks: List<StartupTask>) {
        if (hasFinishedSuccessfully || (hasStarted && activeJob?.isActive == true)) {
            return
        }
        hasStarted = true
        registeredTasks = tasks
        runPendingTasks()
    }

    fun retry() {
        if (hasFinishedSuccessfully || activeJob?.isActive == true) return
        runPendingTasks()
    }

    private fun runPendingTasks() {
        val tasks = registeredTasks
        if (tasks.isEmpty()) {
            finishSuccessfully()
            return
        }

        val totalEssentialWeight = tasks
            .filter { it.isEssential }
            .sumOf { it.weight.toDouble() }
            .toFloat()
            .takeIf { it > 0f }

        val indeterminate = totalEssentialWeight == null

        activeJob = scope.launch {
            _uiState.value = _uiState.value.copy(
                isVisible = true,
                isIndeterminate = indeterminate,
                errorMessage = null
            )

            var completedWeight = tasks
                .filter { it.isEssential && it.id in completedTaskIds }
                .sumOf { it.weight.toDouble() }
                .toFloat()

            for (task in tasks) {
                if (task.id in completedTaskIds) continue

                _uiState.value = _uiState.value.copy(
                    isVisible = true,
                    statusMessage = task.statusLabel,
                    isIndeterminate = indeterminate,
                    errorMessage = null
                )

                try {
                    withContext(Dispatchers.IO) {
                        task.block()
                    }
                    completedTaskIds.add(task.id)
                    if (task.isEssential && totalEssentialWeight != null) {
                        completedWeight += task.weight
                        val ratio = (completedWeight / totalEssentialWeight).coerceIn(0f, 0.99f)
                        _uiState.value = _uiState.value.copy(
                            progress = ratio,
                            statusMessage = if (ratio >= 0.95f) "Almost ready..." else task.statusLabel
                        )
                    }
                } catch (t: Throwable) {
                    if (t is kotlinx.coroutines.CancellationException) throw t
                    if (task.isEssential) {
                        // Essential startup failure: NEVER display 100%, allow safe retry
                        _uiState.value = _uiState.value.copy(
                            isVisible = true,
                            isCompleted = false,
                            isIndeterminate = false,
                            statusMessage = "Startup interrupted",
                            errorMessage = t.message?.takeIf { it.isNotBlank() } ?: "Unable to complete startup initialisation."
                        )
                        return@launch
                    }
                    // Non-essential task failed: record and continue without blocking app startup
                    completedTaskIds.add(task.id)
                }
            }

            finishSuccessfully()
        }
    }

    private fun finishSuccessfully() {
        hasFinishedSuccessfully = true
        _uiState.value = StartupLoaderUiState(
            isVisible = true,
            progress = 1f,
            isIndeterminate = false,
            statusMessage = "Almost ready...",
            isCompleted = true,
            errorMessage = null
        )
        scope.launch {
            if (completionTransitionDelayMs > 0L) {
                delay(completionTransitionDelayMs)
            }
            _uiState.value = _uiState.value.copy(isVisible = false)
        }
    }
}
