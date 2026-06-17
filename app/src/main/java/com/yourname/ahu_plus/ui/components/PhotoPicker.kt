package com.yourname.ahu_plus.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Photo Picker 启动器封装 (无需运行时权限)。
 *
 * 使用 Android 13+ 系统的 Photo Picker (PickVisualMedia),无需 READ_MEDIA_IMAGES 权限。
 *
 * 典型用法:
 * ```
 * val photoPicker = rememberPhotoPicker()
 * Button(onClick = { photoPicker.launch() }) { Text("添加图片") }
 *
 * photoPicker.uri?.let { uri ->
 *     LaunchedEffect(uri) {
 *         val path = viewModel.copyPickedImage(uri)
 *         // ...
 *     }
 * }
 * ```
 */
class PhotoPickerState {
    /** 选中的图片 Uri,未选中时为 null */
    var uri: Uri? = null
        internal set

    /** 启动 Photo Picker */
    fun launch() {
        uri = null
        // 实际 launch 由 [rememberPhotoPicker] 内部注入
        launcherFn?.invoke()
    }

    /** 清除当前选中 (例如已处理后) */
    fun clear() { uri = null }

    internal var launcherFn: (() -> Unit)? = null
}

/**
 * 创建一个 [PhotoPickerState],用于在 Composable 中启动系统 Photo Picker。
 *
 * 关键: 同一个 state 实例被 ActivityResult launcher 和外部使用共享。
 */
@Composable
fun rememberPhotoPicker(): PhotoPickerState {
    val state = remember { PhotoPickerState() }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        // 同一 state 实例,选完后外部 observe state.uri 就能收到
        state.uri = uri
    }
    // 注入 launcher 函数 (一次性,onClick 时调用)
    state.launcherFn = {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    return state
}
