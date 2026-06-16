package com.yourname.ahu_plus.ui.screen.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.model.FinanceSummary
import com.yourname.ahu_plus.data.model.StudentInfoField
import com.yourname.ahu_plus.data.repository.FinanceRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import com.yourname.ahu_plus.data.local.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinanceViewModel(
    private val repository: FinanceRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        FinanceUiState(
            isLoading = false,
            lastUpdatedAt = 0L,
            summary = null
        )
    )
    val uiState: StateFlow<FinanceUiState> = _uiState.asStateFlow()

    init {
        val cached = repository.readCached()
        if (cached != null) {
            _uiState.update { it.copy(summary = cached, lastUpdatedAt = cached.lastUpdatedAt) }
        }
    }

    fun refreshFinance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = withContext(Dispatchers.IO) { repository.getFinanceSummary() }
            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            summary = summary,
                            lastUpdatedAt = sessionManager.getStudentInfoUpdatedAt()
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "财务信息更新失败",
                            needsLogin = e is SessionExpiredException
                        )
                    }
                }
            )
        }
    }
}

data class FinanceUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val summary: FinanceSummary? = null,
    val lastUpdatedAt: Long = 0L
)
