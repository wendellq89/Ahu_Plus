package com.yourname.ahu_plus.ui.screen.schedule.sections

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.data.model.course.AssessmentPlan
import com.yourname.ahu_plus.data.repository.AssessmentRepository
import com.yourname.ahu_plus.ui.screen.schedule.components.CollapsibleSection
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory

/** 考核方案文字最大字符数 */
private const val TEXT_MAX_LEN = 1000

/**
 * 考核方案 section (Step 4: 支持文字 + 多张图片)。
 *
 * 内部维护 text / images 草稿。保存时调 [onSave] 回调,父组件负责
 * 把 plan 写入 AssessmentRepository。
 */
@Composable
fun AssessmentSection(
    plan: AssessmentPlan?,
    lessonId: String,
    assessmentRepository: AssessmentRepository,
    onSave: (text: String, imagePaths: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(lessonId) { mutableStateOf(plan?.text.orEmpty()) }
    var images by remember(lessonId) { mutableStateOf(plan?.imagePaths.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 2026-06-17 Bug5 修复: 直接用 rememberLauncherForActivityResult,
    // 不再通过 PhotoPickerState 包装 (避免跨对象状态共享问题)。
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        pickedImageUri = uri
    }

    // 监听选中的 Uri -> 复制到 filesDir 并加入 images 列表
    pickedImageUri?.let { uri ->
        LaunchedEffect(uri) {
            saving = true
            try {
                val path = assessmentRepository.copyPickedImage(uri, lessonId)
                if (path != null) {
                    images = images + path
                }
            } finally {
                saving = false
                pickedImageUri = null
            }
        }
    }

    CollapsibleSection(
        title = "考核方案",
        defaultExpanded = false,
        badge = if (images.isNotEmpty()) "${images.size} 张" else null,
        modifier = modifier,
    ) {
        Text(
            text = "支持文字 + 图片。记录考核方式、占比、复习重点、往年题目截图等。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = text,
            onValueChange = { newText -> if (newText.length <= TEXT_MAX_LEN) text = newText },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),
            placeholder = { Text("例如:开卷考 60% + 平时作业 40%;重点章节 3、5、7…") },
            supportingText = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${text.length} / $TEXT_MAX_LEN",
                        modifier = Modifier.align(Alignment.CenterEnd),
                        color = if (text.length >= TEXT_MAX_LEN) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            maxLines = 6,
        )

        // 图片缩略图横排
        if (images.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(images, key = { it }) { path ->
                    AssessmentImageThumb(
                        path = path,
                        assessmentRepository = assessmentRepository,
                        onRemove = { images = images - path },
                    )
                }
            }
        }

        // 添加图片按钮 + 保存/还原按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                label = { Text("添加图片") },
                leadingIcon = {
                    Icon(
                        Icons.Filled.AddPhotoAlternate, contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                enabled = !saving,
            )
            Spacer(modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    text = plan?.text.orEmpty()
                    images = plan?.imagePaths.orEmpty()
                },
                enabled = !saving && (text != plan?.text.orEmpty() || images != plan?.imagePaths.orEmpty()),
            ) { Text("还原") }
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        try { onSave(text, images) } finally { saving = false }
                    }
                },
                enabled = !saving && (text != plan?.text.orEmpty() || images != plan?.imagePaths.orEmpty()),
            ) {
                if (saving) CircularProgressIndicator(
                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                ) else Text("保存方案")
            }
        }
    }
}
@Composable
private fun AssessmentImageThumb(
    path: String,
    assessmentRepository: AssessmentRepository,
    onRemove: () -> Unit,
) {
    val file = remember(path) { assessmentRepository.resolveImagePath(path) }
    val bitmap = remember(path) {
        runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
            )
        } else {
            Text(
                text = "无法加载",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 删除按钮
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(20.dp)
                .clickable { onRemove() },
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(2.dp)
                    .size(14.dp),
            )
        }
    }
}

