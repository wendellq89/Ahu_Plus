package com.yourname.ahu_plus.ui.screen.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.ui.screen.schedule.components.CollapsibleSection

/**
 * 课表显示设置 BottomSheet (2026-06-17 重构)。
 *
 * 包含两个可折叠 group:
 *  - 外观设置 (colWidth / rowHeight / fontScale 三个 slider)
 *  - 显示设置 (是否展示周六/周日 / 是否支持左右滑动切换周 / 进入课表是否重置为本周 / 是否展示已完成任务)
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsSheet(
    colWidthDp: Float,
    rowHeightDp: Float,
    fontScale: Float,
    showSat: Boolean,
    showSun: Boolean,
    pagerEnabled: Boolean,
    resetOnEnter: Boolean,
    onColWidthChanged: (Float) -> Unit,
    onRowHeightChanged: (Float) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onShowSatChanged: (Boolean) -> Unit,
    onShowSunChanged: (Boolean) -> Unit,
    onPagerEnabledChanged: (Boolean) -> Unit,
    onResetOnEnterChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "课表显示设置",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset) { Text("恢复默认") }
            }

            // ── 外观设置 (折叠) ────────────────────
            CollapsibleSection(title = "外观设置", defaultExpanded = false) {
                SettingSlider(
                    label = "列宽",
                    value = colWidthDp,
                    valueRange = 48f..80f,
                    steps = 15,
                    valueText = "${colWidthDp.toInt()} dp",
                    onValueChange = onColWidthChanged,
                )
                SettingSlider(
                    label = "行高",
                    value = rowHeightDp,
                    valueRange = 44f..72f,
                    steps = 13,
                    valueText = "${rowHeightDp.toInt()} dp",
                    onValueChange = onRowHeightChanged,
                )
                SettingSlider(
                    label = "字体缩放",
                    value = fontScale,
                    valueRange = 0.75f..1.5f,
                    steps = 14,
                    valueText = "×${"%.2f".format(fontScale)}",
                    onValueChange = onFontScaleChanged,
                )
            }

            // ── 显示设置 (折叠) ────────────────────
            CollapsibleSection(title = "显示设置", defaultExpanded = false) {
                SettingsSwitchRow(
                    title = "展示周六",
                    subtitle = "关闭后周六列隐藏 (如有课会弹确认)",
                    checked = showSat,
                    onCheckedChange = onShowSatChanged,
                )
                SettingsSwitchRow(
                    title = "展示周日",
                    subtitle = "关闭后周日列隐藏 (如有课会弹确认)",
                    checked = showSun,
                    onCheckedChange = onShowSunChanged,
                )
                SettingsSwitchRow(
                    title = "左右滑动切换周",
                    subtitle = "开启后可在课表内左右滑切换周次",
                    checked = pagerEnabled,
                    onCheckedChange = onPagerEnabledChanged,
                )
                SettingsSwitchRow(
                    title = "进入课表重置为本周",
                    subtitle = "每次从首页进入课表时,自动跳到当前周",
                    checked = resetOnEnter,
                    onCheckedChange = onResetOnEnterChanged,
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.padding(horizontal = 4.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                valueText, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
