package com.yourname.ahu_plus.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import com.yourname.ahu_plus.data.repository.YcardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val repository: CardRepository,
    private val casAuthRepository: CasAuthRepository,
    private val ycardRepository: YcardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ensureValidSession() }
            // 余额和账单访问不同的服务器,可并行加载
            loadBalanceAndBills()
        }
    }

    private fun loadBalanceAndBills() {
        viewModelScope.launch {
            coroutineScope {
                val balanceJob = async { loadBalance() }
                val billsJob = async { loadBills() }
                // 等待两者都完成(任一失败不影响另一方)
                balanceJob.await()
                billsJob.await()
            }
        }
    }

    /**
     * 验证 CAS 会话是否有效;若失败标记 needsLogin 让 UI 跳到登录页。
     */
    private suspend fun ensureValidSession() {
        casAuthRepository.ensureValidSession().fold(
            onSuccess = { /* 会话有效 */ },
            onFailure = {
                _uiState.update { it.copy(needsLogin = true) }
            }
        )
    }

    /**
     * 加载余额:如果检测到 JSESSIONID 失效,尝试重新登录一次再重试。
     */
    fun loadBalance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            withContext(Dispatchers.IO) {
                var result: Result<CardRepository.PortalBalance> = repository.getPortalBalance()
                // 检测 session 失效 → 自动重新登录 + 重试
                if (result.exceptionOrNull() is SessionExpiredException) {
                    Log.w("HomeVM", "余额接口报 session 失效，尝试重新登录")
                    val reLogin = casAuthRepository.ensureValidSession()
                    result = if (reLogin.isSuccess) {
                        repository.getPortalBalance()
                    } else {
                        Result.failure(reLogin.exceptionOrNull() ?: Exception("登录失败"))
                    }
                }
                result.fold(
                    onSuccess = { portal ->
                        _uiState.update {
                            it.copy(
                                balance = portal.balance,
                                timestamp = portal.timestamp,
                                isLoading = false
                            )
                        }
                    },
                    onFailure = { e ->
                        if (e is SessionExpiredException ||
                            e.message?.contains("未登录") == true) {
                            _uiState.update { it.copy(needsLogin = true) }
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = e.message ?: "查询失败"
                            )
                        }
                    }
                )
            }
        }
    }

    fun loadBills() {
        viewModelScope.launch {
            _uiState.update { it.copy(billsLoading = true) }
            withContext(Dispatchers.IO) {
                ycardRepository.getBills(1, 20).fold(
                    onSuccess = { resp ->
                        _uiState.update {
                            it.copy(
                                bills = resp.data?.records ?: emptyList(),
                                billsLoading = false,
                                billsError = null
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                billsLoading = false,
                                billsError = e.message ?: "账单查询失败"
                            )
                        }
                    }
                )
            }
        }
    }

    fun onRefresh() {
        loadBalanceAndBills()
    }
}

data class HomeUiState(
    val needsLogin: Boolean = false,
    val balance: Double = 0.0,
    val timestamp: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // 账单
    val bills: List<BillRecord> = emptyList(),
    val billsLoading: Boolean = false,
    val billsError: String? = null
)