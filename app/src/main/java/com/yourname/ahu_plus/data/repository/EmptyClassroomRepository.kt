package com.yourname.ahu_plus.data.repository

import android.util.Log
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.model.AhuUnitTimes
import com.yourname.ahu_plus.data.model.jw.FreeRoom
import com.yourname.ahu_plus.data.model.jw.FreeRoomRequest
import com.yourname.ahu_plus.data.model.jw.FreeRoomResponse
import com.yourname.ahu_plus.data.model.jw.DateTimeSegmentCmd
import com.yourname.ahu_plus.data.network.SecureHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate

/**
 * 空教室查询仓库。
 *
 * 端点：`POST /student/ws/room-borrow/free-list`
 * 鉴权：复用 [JwAuthRepository.jwCookieJar] 的 JW SESSION cookie。
 *
 * 核心算法 —— 滑动窗口 + 并行请求：
 * 1. 确定当前节次，计算剩余节次列表 [now, ..., end]
 * 2. 并行发起 N 次 API 调用，窗口从大到小递减
 * 3. 每间教室的连续空闲节次 = 它出现的最大窗口大小
 * 4. 结果按空闲节次降序、教室名升序排列
 */
class EmptyClassroomRepository(
    private val jwAuthRepository: JwAuthRepository
) {
    private val gson = GsonProvider.instance
    private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()

    private val client: OkHttpClient = SecureHttpClientFactory.create(
        cookieJar = jwAuthRepository.jwCookieJar,
        followRedirects = false,
        disableGzip = false,
        extraInterceptors = listOf(
            okhttp3.Interceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", UA)
                    .header("x-requested-with", "XMLHttpRequest")
                    .build()
                chain.proceed(req)
            }
        )
    )

    /**
     * 查询在指定 [units] 全部空闲的教室。
     */
    suspend fun getFreeRooms(
        buildingId: String,
        campusId: String,
        units: List<Int>,
        date: LocalDate = LocalDate.now()
    ): Result<List<FreeRoom>> = withContext(Dispatchers.IO) {
        try {
            val dateStr = date.toString()
            val requestBody = FreeRoomRequest(
                buildingId = buildingId,
                campusId = campusId,
                dateTimeSegmentCmd = DateTimeSegmentCmd(
                    startDateTime = dateStr,
                    endDateTime = dateStr,
                    units = units.map { it.toString() }
                )
            )
            val jsonBody = gson.toJson(requestBody)
            Log.d(TAG, "free-list request: campus=$campusId building=$buildingId units=$units")

            val request = Request.Builder()
                .url(FREE_LIST_URL)
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json, text/plain, */*")
                .header("Referer", "$JW_BASE/student/for-std/room-borrow")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 302) {
                    throw SessionExpiredException()
                }
                if (!response.isSuccessful) {
                    throw Exception("空教室查询失败: HTTP ${response.code}")
                }
                val body = response.body?.string() ?: ""
                if (body.isBlank()) {
                    throw Exception("空教室查询返回空响应")
                }
                if (body[0] != '{') {
                    throw Exception("空教室查询返回非 JSON: ${body.take(200)}")
                }
                val parsed = gson.fromJson(body, FreeRoomResponse::class.java)
                val rooms = parsed.roomList
                Log.i(TAG, "free-list: ${rooms.size} rooms (campus=$campusId building=$buildingId units=$units)")
                Result.success(rooms)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFreeRooms 失败 (campus=$campusId building=$buildingId units=$units)", e)
            Result.failure(e)
        }
    }

    /**
     * 滑动窗口并行查询 + 空闲持续时间计算。
     *
     * 对 [currentUnit] 起的所有剩余节次，并行发起逐级缩小的窗口调用。
     * 每间教室的空闲节次数 = 它出现的最大窗口大小。
     *
     * @return 按空闲节次降序、教室名升序排列的 [FreeRoomResult] 列表
     */
    suspend fun getFreeRoomsWithDuration(
        buildingId: String,
        campusId: String,
        currentUnit: Int,
        date: LocalDate = LocalDate.now()
    ): Result<List<FreeRoomResult>> = withContext(Dispatchers.IO) {
        try {
            val remaining = AhuUnitTimes.getRemainingUnits(currentUnit)
            if (remaining.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // 并行发起 N 个窗口调用（N = remaining.size）
            val windowResults = coroutineScope {
                remaining.indices.map { windowSize ->
                    val windowUnits = remaining.take(windowSize + 1)
                    async {
                        windowUnits.size to getFreeRooms(buildingId, campusId, windowUnits, date)
                    }
                }.awaitAll()
            }

            // roomId → 最大空闲窗口大小
            val roomToMaxWindow = mutableMapOf<Int, Int>()
            // roomId → 去重后的 FreeRoom 对象
            val roomMap = mutableMapOf<Int, FreeRoom>()

            for ((windowSize, result) in windowResults) {
                result.getOrNull()?.forEach { room ->
                    roomMap.putIfAbsent(room.id, room)
                    val current = roomToMaxWindow[room.id] ?: 0
                    if (windowSize > current) {
                        roomToMaxWindow[room.id] = windowSize
                    }
                }
            }

            val results = roomMap.values.map { room ->
                val freeUnits = roomToMaxWindow[room.id] ?: 1
                val freeUnitNumbers = remaining.take(freeUnits)
                FreeRoomResult(
                    room = room,
                    freeUnitsCount = freeUnits,
                    freeUnitNumbers = freeUnitNumbers,
                    freeTimeRange = AhuUnitTimes.formatUnitRange(freeUnitNumbers)
                )
            }.sortedWith(
                compareByDescending<FreeRoomResult> { it.freeUnitsCount }
                    .thenBy { it.room.nameZh }
            )

            Log.i(TAG, "getFreeRoomsWithDuration: ${results.size} rooms (building=$buildingId, " +
                "currentUnit=$currentUnit, remaining=${remaining.size} units)")
            Result.success(results)
        } catch (e: Exception) {
            Log.e(TAG, "getFreeRoomsWithDuration 失败", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "EmptyClassroomRepo"
        private const val JW_BASE = "https://jw.ahu.edu.cn"
        private const val FREE_LIST_URL = "$JW_BASE/student/ws/room-borrow/free-list"
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0"
    }
}

/**
 * 单间教室的空闲查询结果（UI 层使用）。
 */
data class FreeRoomResult(
    val room: FreeRoom,
    val freeUnitsCount: Int,
    val freeUnitNumbers: List<Int>,
    val freeTimeRange: String
)
