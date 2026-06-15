package com.yourname.ahu_plus.ui.screen.autologin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 自动登录 ViewModel。
 *
 * 启动流程:
 *  - [AutoLoginState.Loading]   调用 casAuthRepository.login()
 *  - 成功 → [AutoLoginState.Success]  (UI 收到后跳转到主页)
 *  - 失败 → [AutoLoginState.Failed]   (UI 显示"点击任意处重试",点击后调用 [retry])
 */
class AutoLoginViewModel(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository,
    private val ycardRepository: YcardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AutoLoginState>(AutoLoginState.Loading)
    val uiState: StateFlow<AutoLoginState> = _uiState.asStateFlow()

    init {
        attemptLogin()
    }

    /** 首次尝试登录(也供重试用) */
    fun attemptLogin() {
        val username = sessionManager.getUsername()
        val password = sessionManager.getPassword()
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            // 没有凭据,跳回登录页
            _uiState.value = AutoLoginState.NoCredentials
            return
        }
        _uiState.value = AutoLoginState.Loading
        viewModelScope.launch {
            val casResult = withContext(Dispatchers.IO) {
                casAuthRepository.login(username, password)
            }
            casResult.fold(
                onSuccess = {
                    // CAS 成功后顺手登录 ycard(失败不报错,继续往下走)
                    val ycardResult = withContext(Dispatchers.IO) {
                        ycardRepository.login(username, password)
                    }
                    ycardResult.onFailure {
                        // 仅记录日志,不阻塞
                        android.util.Log.w("AutoLogin", "ycard 登录失败: ${it.message}")
                    }
                    _uiState.value = AutoLoginState.Success
                },
                onFailure = { e ->
                    _uiState.value = AutoLoginState.Failed(e.message ?: "未知错误")
                }
            )
        }
    }

    /** 用户点击重试 */
    fun retry() {
        attemptLogin()
    }

    /** 用户主动退出登录,清除所有凭据和 session,跳回登录页 */
    fun logout() {
        viewModelScope.launch {
            casAuthRepository.clearCookies() // 纯内存操作,无需切换线程
            sessionManager.clearAll()        // DataStore edit 自带调度器
            _uiState.value = AutoLoginState.NoCredentials
        }
    }
}

sealed class AutoLoginState {
    data object Loading : AutoLoginState()
    data object Success : AutoLoginState()
    data class Failed(val message: String) : AutoLoginState()
    data object NoCredentials : AutoLoginState()
}