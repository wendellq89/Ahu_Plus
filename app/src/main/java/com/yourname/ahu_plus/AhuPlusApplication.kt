package com.yourname.ahu_plus

import android.app.Application
import android.util.Log
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.local.CourseNoteRepository
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.repository.AdwmhCardRepository
import com.yourname.ahu_plus.data.repository.AiCommentRepository
import com.yourname.ahu_plus.data.repository.AssessmentRepository
import com.yourname.ahu_plus.data.repository.ChaoxingNotificationRepository
import com.yourname.ahu_plus.data.repository.ChaoxingRepository
import com.yourname.ahu_plus.data.repository.CloudBackupManager
import com.yourname.ahu_plus.data.repository.CloudStorageRepository
import com.yourname.ahu_plus.data.repository.ChaoxingStudyRepository
import com.yourname.ahu_plus.data.repository.ChaoxingTikuRepository
import com.yourname.ahu_plus.data.repository.KqAttendanceRepository
import com.yourname.ahu_plus.data.update.UpdateManager
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.CourseRepository
import com.yourname.ahu_plus.data.repository.EmptyClassroomRepository
import com.yourname.ahu_plus.data.repository.ExamRepository
import com.yourname.ahu_plus.data.repository.FinanceRepository
import com.yourname.ahu_plus.data.repository.GradeRepository
import com.yourname.ahu_plus.data.repository.HomeworkRepository
import com.yourname.ahu_plus.data.repository.ProgramCompletionRepository
import com.yourname.ahu_plus.data.repository.TrainingPlanRepository
import com.yourname.ahu_plus.data.repository.ExamDataRepository
import com.yourname.ahu_plus.data.repository.JwcNoticeRepository
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.MarketRepository
import com.yourname.ahu_plus.data.repository.RecordRepository
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.UserTaskRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import com.yourname.ahu_plus.notification.WidgetUpdateScheduler
import com.yourname.ahu_plus.util.CxFontDecoder
import org.conscrypt.Conscrypt
import java.security.Security

class AhuPlusApplication : Application() {
    lateinit var appDataStore: AppDataStore
        private set
    lateinit var sessionManager: SessionManager
        private set
    lateinit var courseNoteRepository: CourseNoteRepository
        private set
    lateinit var cardRepository: CardRepository
        private set
    lateinit var casAuthRepository: CasAuthRepository
        private set
    lateinit var jwAuthRepository: JwAuthRepository
        private set
    lateinit var courseRepository: CourseRepository
        private set
    lateinit var ycardRepository: YcardRepository
        private set
    lateinit var marketRepository: MarketRepository
        private set
    lateinit var aiCommentRepository: AiCommentRepository
        private set
    lateinit var jwcNoticeRepository: JwcNoticeRepository
        private set
    lateinit var studentInfoRepository: StudentInfoRepository
        private set
    lateinit var gradeRepository: GradeRepository
        private set
    lateinit var examRepository: ExamRepository
        private set
    lateinit var emptyClassroomRepository: EmptyClassroomRepository
        private set
    lateinit var financeRepository: FinanceRepository
        private set
    lateinit var attendanceRepository: KqAttendanceRepository
        private set
    lateinit var trainingPlanRepository: TrainingPlanRepository
        private set
    lateinit var programCompletionRepository: ProgramCompletionRepository
        private set
    lateinit var adwmhCardRepository: AdwmhCardRepository
        private set
    lateinit var assessmentRepository: AssessmentRepository
        private set
    lateinit var recordRepository: RecordRepository
        private set
    lateinit var homeworkRepository: HomeworkRepository
        private set
    lateinit var userTaskRepository: UserTaskRepository
        private set
    lateinit var initCoordinator: com.yourname.ahu_plus.data.repository.InitCoordinator
        private set
    /**
     * 首次登录初始化消息流 (LoginScreen 触发 → MainScreen 订阅 → 底部 Snackbar 显示 1 秒)。
     * 使用 MutableSharedFlow 而非 StateFlow,因为消息不需要保留"最新值"——重复消息不应被吞掉。
     */
    val initMessageFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 16
    )
    lateinit var chaoxingRepository: ChaoxingRepository
        private set
    lateinit var chaoxingTikuRepository: ChaoxingTikuRepository
        private set
    lateinit var chaoxingStudyRepository: ChaoxingStudyRepository
        private set
    lateinit var chaoxingNotificationRepository: ChaoxingNotificationRepository
        private set
    lateinit var cloudStorageRepository: CloudStorageRepository
        private set
    lateinit var cloudBackupManager: CloudBackupManager
        private set
    lateinit var updateManager: UpdateManager
        private set
    lateinit var examDataRepository: ExamDataRepository
        private set
    override fun onCreate() {
        super.onCreate()

        // ── Conscrypt 强制 TLS(2026-06-22 借鉴 AHUTong) ─────
        // 用 Google 维护的 BoringSSL 移植覆盖 Android 默认 TLS 实现,
        // 解决部分国产 ROM(华为/小米/OPPO)的 TLS 握手异常。
        // insertProviderAt(pos=1) 保证 Conscrypt 优先级高于系统默认,
        // 但低于 BC 等用户安装的 provider。
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
            Log.i("AhuPlusApp", "Conscrypt TLS provider 已加载: ${Conscrypt.newProvider().name}")
        } catch (e: Throwable) {
            // 极端 ROM 可能加载失败(如 classloader 异常),降级到系统默认实现,
            // 不影响主流程。
            Log.w("AhuPlusApp", "Conscrypt 加载失败,使用系统默认 TLS: ${e.message}")
        }

        // ── 基础存储层 ─────────────────────────────
        appDataStore = AppDataStore(this)
        sessionManager = SessionManager(appDataStore)
        courseNoteRepository = CourseNoteRepository(appDataStore)

        // ── 认证层 ────────────────────────────────
        casAuthRepository = CasAuthRepository(sessionManager)
        jwAuthRepository = JwAuthRepository(sessionManager, casAuthRepository)

        // ── 业务 Repository ────────────────────────
        courseRepository = CourseRepository(jwAuthRepository)
        // ycard 复用 CasAuthRepository 的 CASTGC,避免重复完整登录
        ycardRepository = YcardRepository(casAuthRepository)
        cardRepository = CardRepository(
            portalJsessionIdProvider = { casAuthRepository.getJsessionid() }
        )
        marketRepository = MarketRepository(sessionManager)
        aiCommentRepository = AiCommentRepository(this, sessionManager)
        jwcNoticeRepository = JwcNoticeRepository()
        studentInfoRepository = StudentInfoRepository(sessionManager, casAuthRepository)
        // 成绩 / 考试 复用 JwAuthRepository 的 CookieJar
        gradeRepository = GradeRepository(jwAuthRepository)
        examRepository = ExamRepository(jwAuthRepository)
        emptyClassroomRepository = EmptyClassroomRepository(jwAuthRepository)
        // 排考预测:从 Gitee yao-enqi/ahu-plus-update 仓库拉取标准化 JSON,
        // 不再走 jwapp JWT 登录流程 (2026-06-23 重构)。
        examDataRepository = ExamDataRepository(sessionManager)
        trainingPlanRepository = TrainingPlanRepository(jwAuthRepository)
        programCompletionRepository = ProgramCompletionRepository(jwAuthRepository)
        // 财务汇总 / 考勤缺勤 复用 studentInfoRepository 的 SSO 会话 (tp_ep_stu)
        financeRepository = FinanceRepository(sessionManager, casAuthRepository)
        attendanceRepository = KqAttendanceRepository(casAuthRepository, sessionManager)
        adwmhCardRepository = AdwmhCardRepository(sessionManager)
        // 课表 2.0 仓储 (2026-06-17)
        assessmentRepository = AssessmentRepository(appDataStore, this)
        recordRepository = RecordRepository(sessionManager)
        homeworkRepository = HomeworkRepository(sessionManager)
        userTaskRepository = UserTaskRepository(sessionManager)
        // 超星学习通 (2026-06-20)
        chaoxingRepository = ChaoxingRepository(sessionManager)
        chaoxingTikuRepository = ChaoxingTikuRepository(sessionManager)
        chaoxingNotificationRepository = ChaoxingNotificationRepository(sessionManager)
        chaoxingStudyRepository = ChaoxingStudyRepository(
            chaoxingRepository, chaoxingTikuRepository, sessionManager,
            notificationRepo = chaoxingNotificationRepository,
            context = this,
        )
        // 腾讯云 COS 云存储 (2026-06-20)
        cloudStorageRepository = CloudStorageRepository(this)
        cloudBackupManager = CloudBackupManager(this, sessionManager, appDataStore, cloudStorageRepository)

        // 首次登录初始化协调器 (2026-06-22 新增) — 串行预热 7 项核心数据
        initCoordinator = com.yourname.ahu_plus.data.repository.InitCoordinator(
            sessionManager = sessionManager,
            casAuthRepository = casAuthRepository,
            studentInfoRepository = studentInfoRepository,
            ycardRepository = ycardRepository,
            gradeRepository = gradeRepository,
            examRepository = examRepository,
            trainingPlanRepository = trainingPlanRepository,
            kqAttendanceRepository = attendanceRepository,
        )
        // 将 backupManager 注入 sessionManager，供数据变更时触发防抖备份
        sessionManager.setBackupManager(cloudBackupManager)

        // 初始化超星加密字体解码器(2026-06-20 集成 Phase 1)
        // 启动时一次性加载 assets/font_map_table.json (1.6MB) 到内存 hash map。
        CxFontDecoder.init(this)

        // 自动更新管理器
        updateManager = UpdateManager(this, sessionManager)

        // ── Widget / 课程提醒统一调度(2026-06-22 借鉴 AHUTong) ──
        // 首次启动排程,后续每次 scheduleNext 自递归。
        // 放在 onCreate 末尾确保所有 Repository 都已构造完毕。
        WidgetUpdateScheduler.scheduleNext(this)
        WidgetUpdateScheduler.scheduleTicker(this)  // 2026-06-22: 1 分钟 RTC 倒计时刷新
    }

    /**
     * 退出登录时统一清理:
     * 1. SessionManager 清掉持久化数据(DataStore)
     * 2. 各 Repository 清掉内存 Cookie 和 JWT
     *
     * 备注:课程备注 [courseNoteRepository] 不被清空 — 用户重新登录后仍能看到自己之前写的备注。
     */
    suspend fun clearAllSessions() {
        casAuthRepository.clearCookies()
        jwAuthRepository.clearCookies()
        ycardRepository.clearCookies()
        adwmhCardRepository.clearCookies()
        sessionManager.clearAll()
    }

    override fun onTerminate() {
        super.onTerminate()
        cloudStorageRepository.shutdown()
    }
}
