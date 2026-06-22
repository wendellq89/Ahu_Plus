package com.yourname.ahu_plus.ui.screen.chaoxing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 超星学习通总入口。
 * 根据登录状态自动切换：登录页 / 主界面。
 */
@Composable
fun ChaoxingScreen(
    viewModel: ChaoxingViewModel,
    onBack: () -> Unit,
) {
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkLogin()
    }

    if (loginState.isLoggedIn) {
        ChaoxingMainScreen(viewModel = viewModel, onBack = onBack)
    } else {
        ChaoxingLoginScreen(
            loginState = loginState,
            onLogin = viewModel::login,
            onBack = onBack,
            savedPhone = viewModel.sessionManager.getCxPhone() ?: "",
        )
    }
}
