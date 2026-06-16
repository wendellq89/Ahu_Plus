package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.AttendanceRecord
import com.yourname.ahu_plus.data.model.AttendanceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttendanceRepository(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository,
    private val client: StudentTableClient = StudentTableClient(casAuthRepository)
) {
    private val gson = GsonProvider.instance

    /**
     * 拉考勤缺勤列表（全量分页）。
     *
     * - 走 [StudentTableClient.getListData] (POST /cp/templateList/getList)
     * - 数据项 menuId 硬编码: "ep_010_001_88910881" (考勤缺勤)
     * - 服务端 pageid 来自 [getAttendancePageContext] 动态抓取
     * - 遍历所有分页，合并为完整列表
     *
     * @return [AttendanceSummary] 包含 records + total + lastUpdatedAt
     */
    suspend fun getAttendanceList(): Result<AttendanceSummary> {
        return try {
            withContext(Dispatchers.IO) {
                client.activateSession()
                val username = sessionManager.getUsername()
                    ?: return@withContext Result.failure(Exception("未找到当前账号"))
                client.activateStudentRole()
                val dataItems = client.getAllDataItems()
                val item = dataItems.firstOrNull {
                    it.get("NAME")?.asString == "考勤缺勤" ||
                        it.get("DATATYPENAME")?.asString == "考勤缺勤"
                } ?: return@withContext Result.failure(Exception("未在学生一张表找到「考勤缺勤」数据项"))

                val path = item.get("CKLZ")?.asString
                    ?: return@withContext Result.failure(Exception("考勤缺勤缺 CKLZ 路径"))
                val pageContext = client.getPageContext(path)
                    ?: return@withContext Result.failure(Exception("未能解析考勤缺勤 pageid"))

                // 第一页
                val firstPage = client.getListData(pageContext, pageNum = 1, pageSize = ATTENDANCE_PAGE_SIZE)
                val total = firstPage.get("total")?.asInt ?: 0
                val totalPages = firstPage.get("pages")?.asInt ?: 1
                val allRecords = parseRecords(firstPage).toMutableList()

                // 遍历剩余页
                for (page in 2..totalPages) {
                    Log.d(TAG, "考勤缺勤分页: $page/$totalPages")
                    val resp = client.getListData(pageContext, pageNum = page, pageSize = ATTENDANCE_PAGE_SIZE)
                    allRecords.addAll(parseRecords(resp))
                }

                Log.i(TAG, "考勤缺勤 fetched: total=$total, pages=$totalPages, parsed=${allRecords.size}")
                // 按日期降序排列（最近的最靠上）
                allRecords.sortByDescending { it.classDate ?: "" }
                val summary = AttendanceSummary(
                    records = allRecords,
                    total = total,
                    lastUpdatedAt = System.currentTimeMillis()
                )
                persistToCache(summary)
                Result.success(summary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "attendance list error", e)
            Result.failure(e)
        }
    }

    private fun parseRecords(resp: com.google.gson.JsonObject): List<AttendanceRecord> {
        val listArr = resp.getAsJsonArray("list") ?: return emptyList()
        return listArr.mapNotNull { el ->
            if (el.isJsonObject) AttendanceRecord.fromJson(el.asJsonObject) else null
        }
    }

    fun readCached(): AttendanceSummary? {
        val json = sessionManager.getAttendanceJson() ?: return null
        return runCatching {
            gson.fromJson(json, AttendanceSummary::class.java)
        }.getOrNull()
    }

    private suspend fun persistToCache(summary: AttendanceSummary) {
        val json = runCatching { gson.toJson(summary) }.getOrNull() ?: return
        sessionManager.saveAttendanceJson(json)
    }

    companion object {
        private const val TAG = "AttendanceRepo"
        private const val ATTENDANCE_PAGE_SIZE = 50
    }
}
