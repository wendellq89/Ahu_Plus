package com.yourname.ahu_plus.ui.screen.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yourname.ahu_plus.ui.components.AhuShapes

/**
 * 使用指南页面 —— 大分类可折叠（手风琴）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageGuideScreen(onBack: () -> Unit) {
    val sections = remember {
        listOf(
            CollapsibleSectionData("📲 安装与更新") {
                Bullet("在手机上开启「允许安装未知来源应用」：设置 → 安全 → 安装未知应用 → 允许浏览器/文件管理器")
                Bullet("获取 APK 安装包，传到手机上点击安装")
                Bullet("提示「此应用为调试版本」属正常情况，确认即可")
                Spacer(Modifier.height(6.dp))
                TextWithBody("后续更新", "通过应用内「检查更新」功能直接下载安装最新版本即可。如遇到安装失败，可先卸载旧版再安装新版。")
            },
            CollapsibleSectionData("🔐 登录") {
                TextWithBody("统一身份认证登录", "输入学号和统一身份认证密码（即网上办事大厅密码），点击登录即可。登录成功会自动建立校园卡、教务、学生信息等多个系统的会话，无需重复登录。")
                TextWithBody("自动登录", "登录成功后，后续打开 App 会自动尝试恢复会话。如果检测到会话过期，会弹出轻量重认证窗口重新登录。")
            },
            CollapsibleSectionData("🏠 首页（Tab 1）") {
                TextWithBody("今日课程", "显示当天的课程安排。点击课程卡片可查看课程详情（BottomSheet），含课程详情、考核方案、课程备注、此节课备注、考勤记录 5 个折叠 section。")
                TextWithBody("近期任务", "聚合显示近期的考试安排、待办事项、作业截止。点击右侧箭头展开全部任务弹窗。可在「设置」中切换是否显示已完成考试/任务。")
                TextWithBody("最近使用", "显示最近点过的应用入口。点击「更多」进入应用中心。")
                TextWithBody("通知公告", "显示最新的教务处通知，点击进入通知列表查看更多。")
            },
            CollapsibleSectionData("🏪 校园集市（Tab 2）") {
                TextWithBody("配置身份", "需要配置微信小程序的身份 Token：在电脑上用 Token 提取工具获取「Bearer eyJ...」开头的长字符串，在 App 中「我的 → 集市设置 → API 身份字段」粘贴保存。支持多校区。")
                TextWithBody("浏览帖子", "单列模式（默认）或双列瀑布流，下拉刷新，右上角可搜索。")
                TextWithBody("热榜", "单校模式下显示「十大热帖」入口。")
                TextWithBody("帖子详情", "查看正文、图片、评论，多图可滑动浏览，可评论或回复。右上角可导出帖子为长图片。")
                TextWithBody("发帖", "右下角悬浮按钮，选择板块（新鲜事/日常投稿/二手闲置/树洞/表白墙），多校区需选择发帖校区，支持图片和匿名。")
            },
            CollapsibleSectionData("📚 学习通（Tab 3）") {
                TextWithBody("登录", "输入超星学习通（手机号）账号密码，自动 AES 加密登录。")
                TextWithBody("课程", "显示所有课程列表，点击进入详情查看章节和任务点。")
                TextWithBody("自动学习", "可配置视频倍速、自动答题（需题库 provider）、自动完成阅读。任务类型可选。")
                TextWithBody("题库", "支持多 provider 回退链：CACHE → YANXI → GO → LIKE → ADAPTER → AI（默认 DeepSeek）→ SILICONFLOW。在设置中可配置顺序。")
                TextWithBody("消息", "两个子 Tab：收件箱和课程活动。收件箱聚合可合并显示。")
            },
            CollapsibleSectionData("🧩 应用中心（Tab 4）") {
                Bullet("📅 课表 — 学期课程表，课程详情、考核方案、备注、考勤")
                Bullet("📝 成绩 — 多学期成绩 + GPA 汇总 + 柱状图 + 课程明细")
                Bullet("✏️ 考试 — 考试安排列表，已结束自动置灰")
                Bullet("🎓 培养方案 — 方案进度，手风琴折叠各模块")
                Bullet("🏫 空教室 — 按校区/教学楼/楼层查询，带时间轴标记")
                Bullet("📰 通知公告 — 教务处最新通知")
                Bullet("📇 学生信息 — 基本信息、住宿数据、学业预警")
                Bullet("💰 财务 — 奖学金、助学金、贷款、欠费等 6 项汇总")
                Bullet("⏰ 考勤 — 缺勤记录列表，按类型颜色区分")
                Bullet("✉️ 校长信箱 — 向校长反映问题、提交建议")
                Bullet("💧 水电费 — 浴室余额、空调/照明/网费余额及账单")
            },
            CollapsibleSectionData("👤 我的（Tab 5）") {
                TextWithBody("个人资料", "顶部显示姓名、学院、专业信息。")
                TextWithBody("校园卡", "显示一卡通余额，点击进入账单列表，二维码图标打开智慧安大支付码。")
                TextWithBody("校园服务", "水电费查询、校长信箱。")
                Bullet("📋 我的信息 — 基本信息、住宿数据、学业预警、财务信息")
                Bullet("🏪 集市设置 — API Token 配置、列表模式、AI 评论助手")
                Bullet("⚙️ 设置 — 主题模式、显示偏好")
                Bullet("🚪 退出登录 — 清除本地凭据与登录会话")
            },
            CollapsibleSectionData("🎨 主题") {
                Bullet("跟随系统：自动根据系统深色模式切换（默认）")
                Bullet("白天：始终浅色界面")
                Bullet("深色：始终深色界面")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "在「我的 → 设置 → 外观」中切换。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            CollapsibleSectionData("🔒 数据安全") {
                TextWithBody("本地存储", "所有业务数据缓存到本地 DataStore。登录凭据（Cookie、Session）持久化保存。集市 Token 加密存储。")
                TextWithBody("网络传输", "安大域名使用自签名证书，App 内置信任配置。集市 API 使用标准 HTTPS 证书。超星 API 使用 AES/CBC 加密传输密码。")
                Bullet("退出登录会清除所有本地数据和凭据。请谨慎操作。")
            }
        )
    }

    CollapsiblePagesScaffold(
        title = "使用指南",
        sections = sections,
        onBack = onBack
    )
}

/**
 * 常见问题页面 —— 分类可折叠（手风琴）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaqScreen(onBack: () -> Unit) {
    val sections = remember {
        listOf(
            CollapsibleSectionData("登录") {
                QaBlock(
                    "Q：登录成功后有些功能还是不能用？",
                    "不同功能依赖不同的后端系统（一卡通、教务、学生一张表等），登录时会依次建立各系统会话。如果某系统会话建立失败，对应功能会显示错误提示，但不影响其他功能使用。点击对应页面的「刷新」按钮重新触发认证即可。"
                )
            },
            CollapsibleSectionData("数据") {
                QaBlock(
                    "Q：数据多久更新一次？",
                    "大部分功能采用本地优先、网络兜底策略：一卡通余额每次打开实时获取；课表/成绩/考试安排打开时后台静默刷新；一卡通账单首次全量拉取后追加更新；财务/考勤/学生信息需手动点击刷新；集市帖子手动下拉刷新。"
                )
                QaBlock(
                    "Q：为什么余额显示「—」？",
                    "一卡通余额需要连接校园网才能获取。请连接到 AHU WiFi 后刷新重试。余额为零或获取失败时不会显示具体金额，不影响其他功能。"
                )
                QaBlock(
                    "Q：考试安排混入了过往学期的考试？",
                    "教务处接口可能同时返回多个学期的考试安排。已结束的考试会自动置灰显示。可在「设置 → 功能」中关闭「显示已完成考试」来隐藏它们。"
                )
            },
            CollapsibleSectionData("校园集市") {
                QaBlock(
                    "Q：Token 怎么获取？",
                    "使用 tools/ 目录下的提取工具。推荐方法：本地缓存扫描，直接扫描微信本地数据目录，不需要代理和证书。备用方法：代理监听。详细步骤见工具目录下的说明文档。"
                )
                QaBlock(
                    "Q：Token 粘贴后集市还是空白？",
                    "检查：Token 是否完整复制（从「Bearer eyJ」开始）；重新进入集市设置删除旧 Token 重新粘贴保存；多校区用户是否选对了身份；Token 是否已过期。"
                )
                QaBlock(
                    "Q：Token 多久失效？",
                    "Token 有效期约三十天。失效时 App 会提示「集市身份字段已失效」，重新提取新 Token 即可。"
                )
                QaBlock(
                    "Q：「多校模式」怎么用？",
                    "在集市设置中添加多个 Token。启用后帖子列表旁显示学校标签，发帖时需选择目标校区。热榜和消息通知仅单校模式下可用。"
                )
                QaBlock(
                    "Q：发帖匿名有用吗？",
                    "App 支持设置匿名发帖。匿名后帖子不显示你的用户名，但注意集市后端仍可能通过 Token 关联到你的身份。"
                )
            },
            CollapsibleSectionData("学习通（超星）") {
                QaBlock(
                    "Q：自动学习会被检测吗？",
                    "自动学习引擎模拟正常学习行为：视频按设置倍速播放、答题使用题库匹配。建议不要设置极端参数（如 2x 倍速 + 极小间隔），以减少被检测风险。"
                )
                QaBlock(
                    "Q：题库 AI provider 怎么配置？",
                    "默认使用 DeepSeek。在 deepseek.com 注册获取 API Key，在「学习通 → 设置 → 题库设置」中配置，可选切换模型（默认 deepseek-chat）。"
                )
                QaBlock(
                    "Q：字体解码失败怎么办？",
                    "超星使用自定义 TTF 字体加密文字。App 启动时自动加载 30,000+ 映射条目。如仍失败，可能是遇到了新的字体编码，可联系开发者更新映射表。"
                )
            },
            CollapsibleSectionData("错误与故障") {
                QaBlock(
                    "Q：提示「网络错误」",
                    "检查信号是否正常。所有功能均可通过外网使用。部分接口对网络波动敏感，稍后重试。校园网环境下部分功能更稳定。"
                )
                QaBlock(
                    "Q：提示「会话过期」",
                    "有本地缓存时显示缓存数据，不影响使用。无缓存时 App 会弹出轻量重认证窗口。如果频繁出现，可尝试退出重新登录。"
                )
                QaBlock(
                    "Q：App 闪退怎么办？",
                    "清理后台重启 App。如果问题持续，尝试清除应用数据。通过邮件反馈，附带手机型号、Android 版本、操作步骤、截图。"
                )
                QaBlock(
                    "Q：某些功能显示空白但没报错？",
                    "首次使用需要手动刷新才会加载数据。部分功能（如财务、考勤）需要手动点击「更新数据」。如果刷新后仍然空白，可能是后端系统暂时不可用。"
                )
            },
            CollapsibleSectionData("其他") {
                QaBlock(
                    "Q：为什么有些功能需要刷新才会加载？",
                    "为了保护校园网后端系统，大部分功能采用手动刷新或打开时静默刷新策略，不会在后台持续轮询。这也是为了减少不必要的网络请求和耗电。"
                )
                QaBlock(
                    "Q：App 会消耗很多流量吗？",
                    "不会。课表/成绩/考试每次几十 KB，集市帖子每次几十到几百 KB（含图片需单独加载），缓存数据存储在本地，不会重复下载。"
                )
                QaBlock(
                    "Q：隐私安全有保障吗？",
                    "所有数据仅在手机本地和安大/超星/集市服务器之间传输，不经过第三方服务器中转。集市 Token 等同于你的登录态，建议不要分享给他人。App 不收集任何使用统计数据。"
                )
                QaBlock(
                    "Q：有问题或建议怎么反馈？",
                    "开发者邮箱：2867299793@qq.com。反馈时请附带手机型号、Android 版本、问题描述、截图。"
                )
            }
        )
    }

    CollapsiblePagesScaffold(
        title = "常见问题",
        sections = sections,
        onBack = onBack
    )
}

// ── 共享 Scaffold ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsiblePagesScaffold(
    title: String,
    sections: List<CollapsibleSectionData>,
    onBack: () -> Unit
) {
    var expandedIndex by remember { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sections.forEachIndexed { index, section ->
                CollapsibleCard(
                    title = section.title,
                    expanded = expandedIndex == index,
                    onToggle = { expandedIndex = if (expandedIndex == index) -1 else index }
                ) {
                    section.content()
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ── 数据类 ──

private data class CollapsibleSectionData(
    val title: String,
    val content: @Composable () -> Unit
)

// ── 可折叠 Card ──

@Composable
private fun CollapsibleCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        shape = AhuShapes.Card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(initialHeight = { 0 }) + fadeIn(),
                exit = shrinkVertically(targetHeight = { 0 }) + fadeOut()
            ) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                    content()
                }
            }
        }
    }
}

// ── 内容组件 ──

@Composable
private fun Bullet(text: String) {
    Text(
        text = "• $text",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun TextWithBody(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun QaBlock(question: String, answer: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = answer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
