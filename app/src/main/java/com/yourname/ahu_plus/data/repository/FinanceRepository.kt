package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.google.gson.JsonObject
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.FinanceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FinanceRepository(
    private val sessionManager: SessionManager,
    private val casAuthRepository: CasAuthRepository,
    private val client: StudentTableClient = StudentTableClient(casAuthRepository)
) {
    private val gson = GsonProvider.instance

    /**
     * 拉 6 个财务相关数据项,组装 [FinanceSummary]。
     * - 串行调用 (因为 6 个都走同一个 SSO 会话, 并发可能触发服务端限流)
     * - 单项失败 → 整 Result.failure; 客户端只显示"获取失败"
     */
    suspend fun getFinanceSummary(): Result<FinanceSummary> {
        return try {
            withContext(Dispatchers.IO) {
                client.activateSession()
                val username = sessionManager.getUsername()
                    ?: return@withContext Result.failure(Exception("未找到当前账号"))
                client.activateStudentRole()
                val dataItems = client.getAllDataItems()
                val now = System.currentTimeMillis()

                val summary = FinanceSummary(
                    scholarship = fetch(dataItems, "奖学金数据", username),
                    grant = fetch(dataItems, "助学金数据", username),
                    hardshipGrant = fetch(dataItems, "临时困难补助数据", username),
                    workStudy = fetch(dataItems, "勤工助学数据", username),
                    arrearsStatus = fetch(dataItems, "欠费状态", username),
                    loan = fetch(dataItems, "贷款数据", username),
                    lastUpdatedAt = now
                )
                persistToCache(summary)
                Result.success(summary)
            }
        } catch (e: Exception) {
            Log.e(TAG, "finance summary error", e)
            Result.failure(e)
        }
    }

    fun readCached(): FinanceSummary? {
        val json = sessionManager.getFinanceJson() ?: return null
        return runCatching {
            gson.fromJson(json, FinanceSummary::class.java)
        }.getOrNull()
    }

    private suspend fun persistToCache(summary: FinanceSummary) {
        val json = runCatching { gson.toJson(summary) }.getOrNull() ?: return
        sessionManager.saveFinanceJson(json)
    }

    private fun fetch(dataItems: List<JsonObject>, name: String, username: String) =
        dataItems.firstOrNull { it.get("NAME")?.asString == name || it.get("DATATYPENAME")?.asString == name }
            ?.let { item ->
                val total = item.get("TOTE")?.asString
                val fields = runCatching { client.getDataItemFields(item, username) }.getOrNull().orEmpty()
                if (fields.isEmpty() && !total.isNullOrBlank()) {
                    listOf(com.yourname.ahu_plus.data.model.StudentInfoField("记录数", total))
                } else fields
            }
            .orEmpty()

    companion object {
        private const val TAG = "FinanceRepo"
    }
}
