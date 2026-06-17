package com.yourname.ahu_plus.data.model.jw

import com.google.gson.annotations.SerializedName

/**
 * 空教室查询请求体。
 *
 * 接口：`POST /student/ws/room-borrow/free-list`
 */
data class FreeRoomRequest(
    val buildingId: String,
    val campusId: String,
    val roomId: String = "",
    val dateTimeSegmentCmd: DateTimeSegmentCmd,
    val seatsForLessonGte: String = "",
    val hasDataPermission: Boolean = false
)

data class DateTimeSegmentCmd(
    val startDateTime: String,
    val endDateTime: String,
    val startTime: String = "",
    val endTime: String = "",
    val weekdays: List<Int> = emptyList(),
    val units: List<String>
)

/**
 * 空教室查询响应体。
 */
data class FreeRoomResponse(
    @SerializedName("roomList") val roomList: List<FreeRoom>
)

/**
 * 单条空闲教室记录。
 */
data class FreeRoom(
    val id: Int,
    @SerializedName("nameZh") val nameZh: String,
    @SerializedName("nameEn") val nameEn: String? = null,
    val code: String? = null,
    val building: FreeRoomBuilding? = null,
    @SerializedName("roomType") val roomType: FreeRoomType? = null,
    val floor: Int? = null,
    val virtual: Boolean? = null,
    @SerializedName("seatsForLesson") val seatsForLesson: Int? = null,
    val remark: String? = null,
    val seats: Int? = null,
    val week: String? = null,
    val weekday: String? = null,
    val date: String? = null,
    val units: Any? = null,
    @SerializedName("weekNum") val weekNum: Any? = null,
    @SerializedName("mngtDepartAssoc") val mngtDepartAssoc: Int? = null
)

data class FreeRoomBuilding(
    val id: Int? = null,
    @SerializedName("nameZh") val nameZh: String? = null,
    @SerializedName("nameEn") val nameEn: String? = null,
    val code: String? = null,
    val campus: FreeRoomCampus? = null
)

data class FreeRoomCampus(
    val id: Int? = null,
    @SerializedName("nameZh") val nameZh: String? = null,
    @SerializedName("nameEn") val nameEn: String? = null,
    val code: String? = null
)

data class FreeRoomType(
    val id: Int? = null,
    @SerializedName("nameZh") val nameZh: String? = null,
    @SerializedName("nameEn") val nameEn: String? = null,
    val code: String? = null
)
