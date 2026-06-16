package com.yourname.ahu_plus.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.AttendanceSummary
import com.yourname.ahu_plus.data.repository.AttendanceRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AttendanceUiState(
            isLoading = false,
            lastUpdatedAt = 0L,
            summary = null
        )
    )
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    init {
        val cached = repository.readCached()
        if (cached != null) {
            _uiState.update { it.copy(summary = cached, lastUpdatedAt = cached.lastUpdatedAt) }
        }
    }

    fun refreshAttendance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.getAttendanceList() }
            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            summary = summary,
                            lastUpdatedAt = summary.lastUpdatedAt
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "考勤数据更新失败",
                            needsLogin = e is SessionExpiredException
                        )
                    }
                }
            )
        }
    }
}

data class AttendanceUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val summary: AttendanceSummary? = null,
    val lastUpdatedAt: Long = 0L
)
