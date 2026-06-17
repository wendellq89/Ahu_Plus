package com.yourname.ahu_plus.data.repository

import android.app.Application
import com.yourname.ahu_plus.data.local.AppDataStore
import com.yourname.ahu_plus.data.model.course.AssessmentPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 考核方案仓库。
 *
 * 提供对 [AssessmentPlan] 的观察/保存/清除能力,持久化在共享 DataStore
 * (`ahu_plus_prefs`) 的 `assessment_<lessonId>` key 中 (单 key 单 plan,JSON 序列化)。
 *
 * 图片附件: 选图后通过 [copyPickedImage] 复制到 `filesDir/course_assets/<lessonId>/<uuid>.jpg`,
 * 仅保存相对路径,渲染时拼接回完整路径。
 *
 * @param appDataStore 共享 DataStore
 * @param application 用于获取 filesDir (图片附件存储)
 */
class AssessmentRepository(
    private val appDataStore: AppDataStore,
    private val application: Application,
) {

    /** 观察某节课的考核方案 (可能为 null) */
    fun observe(lessonId: String): Flow<AssessmentPlan?> =
        appDataStore.assessmentFlow(lessonId)

    /** 保存考核方案。同步清理 plan.imagePaths 之外的孤立文件。 */
    suspend fun save(plan: AssessmentPlan) = withContext(Dispatchers.IO) {
        val baseDir = courseAssetsDir(plan.lessonId)
        if (baseDir.exists()) {
            // imagePaths 是相对 filesDir 的路径 (如 "course_assets/123/uuid.jpg")
            val keep = plan.imagePaths.map { File(application.filesDir, it).absolutePath }.toSet()
            baseDir.listFiles()?.forEach { f ->
                if (f.isFile && f.absolutePath !in keep) {
                    runCatching { f.delete() }
                }
            }
        }
        appDataStore.saveAssessment(plan)
    }

    /** 清除某节课的考核方案及其附件 */
    suspend fun clear(lessonId: String) = withContext(Dispatchers.IO) {
        appDataStore.clearAssessment(lessonId)
        runCatching {
            val dir = courseAssetsDir(lessonId)
            if (dir.exists()) dir.deleteRecursively()
        }
    }

    /**
     * 把用户从 Photo Picker 选中的图片复制到 filesDir 下的私有目录,
     * 返回相对路径 (相对 filesDir,如 "course_assets/<lessonId>/<uuid>.jpg")。
     */
    suspend fun copyPickedImage(
        pickedUri: android.net.Uri,
        lessonId: String,
    ): String? = withContext(Dispatchers.IO) {
        val dir = courseAssetsDir(lessonId).apply { mkdirs() }
        val fileName = "${UUID.randomUUID()}.jpg"
        val dest = File(dir, fileName)
        val ok = runCatching {
            application.contentResolver.openInputStream(pickedUri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
        }.isSuccess
        if (!ok) {
            if (dest.exists()) dest.delete()
            return@withContext null
        }
        // 返回相对 filesDir 的路径
        "course_assets/$lessonId/$fileName"
    }

    /** 把已保存的相对路径转回绝对路径 (用于渲染) */
    fun resolveImagePath(relativePath: String): File {
        val filesDir: File = application.filesDir
        return if (relativePath.startsWith("course_assets/")) {
            File(filesDir, relativePath)
        } else {
            // 兼容旧数据(可能直接存了文件名)
            File(filesDir, "course_assets/_legacy/$relativePath")
        }
    }

    /** 课程的图片附件目录 (绝对路径) */
    fun courseAssetsDir(lessonId: String): File =
        File(application.filesDir, "course_assets/$lessonId")
}
