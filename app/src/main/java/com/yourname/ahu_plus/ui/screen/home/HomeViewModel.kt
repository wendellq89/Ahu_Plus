package com.yourname.ahu_plus.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.local.ElectricityRoomConfig
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.BathroomBalanceData
import com.yourname.ahu_plus.data.model.BillRecord
import com.yourname.ahu_plus.data.model.ElectricityDailyRecord
import com.yourname.ahu_plus.data.model.ElectricityUiData
import com.yourname.ahu_plus.data.model.InternetBalanceData
import com.yourname.ahu_plus.data.model.InternetBillRecord
import com.yourname.ahu_plus.data.model.StudentInfo
import com.yourname.ahu_plus.data.repository.CardRepository
import com.yourname.ahu_plus.data.repository.CasAuthRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import com.yourname.ahu_plus.data.repository.StudentInfoRepository
import com.yourname.ahu_plus.data.repository.YcardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class HomeViewModel(
    private val repository: CardRepository,
    private val casAuthRepository: CasAuthRepository,
    private val ycardRepository: YcardRepository,
    private val sessionManager: SessionManager,
    private val studentInfoRepository: StudentInfoRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        val savedBathroomPhone = sessionManager.getBathroomPhone().orEmpty()
        _uiState.update {
            it.copy(
                bathroomPhone = savedBathroomPhone,
                ac = it.ac.copy(config = sessionManager.getAcConfig().fillFromLegacyPrefill(ElectricityKind.Ac)),
                lighting = it.lighting.copy(
                    config = sessionManager.getLightingConfig().fillFromLegacyPrefill(ElectricityKind.Lighting)
                )
            )
        }
        applyStudentInfoPrefill(studentInfoRepository?.readCachedStudentInfo(), loadAfterApply = false)
        loadBalanceAndBills()
    }

    fun applyStudentInfoPrefill(info: StudentInfo?, loadAfterApply: Boolean = true) {
        val prefill = info?.toBalancePrefill() ?: return
        val before = _uiState.value

        _uiState.update { state ->
            val bathroomPhone = state.bathroomPhone.ifBlank { prefill.phone.orEmpty() }
            val acConfig = state.ac.config.fillMissingWith(prefill.acConfig)
            val lightingConfig = state.lighting.config.fillMissingWith(prefill.lightingConfig)
            state.copy(
                bathroomPhone = bathroomPhone,
                ac = state.ac.copy(config = acConfig),
                lighting = state.lighting.copy(config = lightingConfig)
            )
        }

        if (!loadAfterApply) return
        val after = _uiState.value
        if (before.bathroomPhone.isBlank() && after.bathroomPhone.isNotBlank()) {
            loadBathroomBalance()
        }
        if (!before.ac.config.isComplete && after.ac.config.isComplete) {
            loadAcBalance()
        }
        if (!before.lighting.config.isComplete && after.lighting.config.isComplete) {
            loadLightingBalance()
        }
    }

    private fun loadBalanceAndBills() {
        viewModelScope.launch {
            coroutineScope {
                val balanceJob = async { loadBalance() }
                val billsJob = async { loadBills() }
                val bathroomJob = async { loadBathroomBalance() }
                val acJob = async { loadAcBalance() }
                val lightingJob = async { loadLightingBalance() }
                val internetJob = async { loadInternetBalance() }
                balanceJob.await()
                billsJob.await()
                bathroomJob.await()
                acJob.await()
                lightingJob.await()
                internetJob.await()
            }
        }
    }

    // ── 校园卡余额 ──────────────────────────────────────

    fun loadBalance() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            withContext(Dispatchers.IO) {
                var result: Result<CardRepository.PortalBalance> = repository.getPortalBalance()
                if (result.exceptionOrNull() is SessionExpiredException) {
                    Log.w("HomeVM", "余额接口报 session 失效，尝试重新登录")
                    val reLogin = casAuthRepository.ensureValidSession()
                    result = if (reLogin.isSuccess) {
                        repository.getPortalBalance()
                    } else {
                        Result.failure(reLogin.exceptionOrNull() ?: Exception("登录失败"))
                    }
                }
                result.fold(
                    onSuccess = { portal ->
                        _uiState.update {
                            it.copy(balance = portal.balance, timestamp = portal.timestamp, isLoading = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message ?: "查询失败") }
                    }
                )
            }
        }
    }

    // ── 账单 ────────────────────────────────────────────

    fun loadBills() {
        viewModelScope.launch {
            _uiState.update { it.copy(billsLoading = true) }
            withContext(Dispatchers.IO) {
                ycardRepository.getAllBills().fold(
                    onSuccess = { records ->
                        _uiState.update { it.copy(bills = records, billsLoading = false, billsError = null) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(billsLoading = false, billsError = e.message ?: "账单查询失败") }
                    }
                )
            }
        }
    }

    // ── 浴室余额 ────────────────────────────────────────

    fun loadBathroomBalance() {
        viewModelScope.launch {
            val phone = _uiState.value.bathroomPhone.ifBlank {
                sessionManager.getBathroomPhone().orEmpty()
            }
            if (phone.isBlank()) {
                _uiState.update { it.copy(bathroomLoading = false) }
                return@launch
            }
            _uiState.update { it.copy(bathroomLoading = true, bathroomError = null) }
            withContext(Dispatchers.IO) {
                ycardRepository.getBathroomBalance(phone).fold(
                    onSuccess = { data ->
                        _uiState.update { it.copy(bathroomData = data, bathroomLoading = false) }
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(bathroomLoading = false, bathroomError = e.message ?: "浴室查询失败") }
                    }
                )
            }
        }
    }

    fun saveBathroomPhone(phone: String) {
        viewModelScope.launch {
            sessionManager.saveBathroomPhone(phone)
            _uiState.update { it.copy(bathroomPhone = phone) }
            loadBathroomBalance()
        }
    }

    // ── 空调余额 ────────────────────────────────────────

    fun loadAcBalance() {
        loadElectricityBalance(
            feeitemid = "408",
            configProvider = { _uiState.value.ac.config },
            stateUpdater = { s, d, e -> s.copy(ac = s.ac.copy(data = d, loading = false, error = e)) },
            loadingUpdater = { s -> s.copy(ac = s.ac.copy(loading = true, error = null)) }
        )
    }

    fun saveAcConfig(config: ElectricityRoomConfig) {
        viewModelScope.launch {
            sessionManager.saveAcConfig(config)
            _uiState.update { it.copy(ac = it.ac.copy(config = config)) }
            loadAcBalance()
            loadAcBills()
        }
    }

    // ── 照明余额 ────────────────────────────────────────

    fun loadLightingBalance() {
        loadElectricityBalance(
            feeitemid = "428",
            configProvider = { _uiState.value.lighting.config },
            stateUpdater = { s, d, e -> s.copy(lighting = s.lighting.copy(data = d, loading = false, error = e)) },
            loadingUpdater = { s -> s.copy(lighting = s.lighting.copy(loading = true, error = null)) }
        )
    }

    fun saveLightingConfig(config: ElectricityRoomConfig) {
        viewModelScope.launch {
            sessionManager.saveLightingConfig(config)
            _uiState.update { it.copy(lighting = it.lighting.copy(config = config)) }
            loadLightingBalance()
            loadLightingBills()
        }
    }

    // ── 网费余额 ────────────────────────────────────────

    fun loadInternetBalance() {
        viewModelScope.launch {
            _uiState.update { it.copy(internetLoading = true, internetError = null) }
            withContext(Dispatchers.IO) {
                ycardRepository.getInternetBalance().fold(
                    onSuccess = { data ->
                        _uiState.update {
                            it.copy(internetData = data, internetLoading = false)
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(internetLoading = false, internetError = e.message ?: "网费查询失败")
                        }
                    }
                )
            }
        }
    }

    // ── 电费通用加载 ────────────────────────────────────

    fun loadInternetBills() {
        viewModelScope.launch {
            _uiState.update { it.copy(internetBillsLoading = true, internetBillsError = null) }
            withContext(Dispatchers.IO) {
                ycardRepository.getInternetBills(row = 20).fold(
                    onSuccess = { response ->
                        _uiState.update {
                            it.copy(
                                internetBills = response.accountList,
                                internetBillsLoading = false,
                                internetBillsError = null
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                internetBillsLoading = false,
                                internetBillsError = e.message ?: "网费账单查询失败"
                            )
                        }
                    }
                )
            }
        }
    }

    fun setAcBillRange(range: ElectricityBillRange) {
        _uiState.update { it.copy(acBillRange = range) }
        loadAcBills(range)
    }

    fun setLightingBillRange(range: ElectricityBillRange) {
        _uiState.update { it.copy(lightingBillRange = range) }
        loadLightingBills(range)
    }

    fun loadAcBills(range: ElectricityBillRange = _uiState.value.acBillRange) {
        loadElectricityBills(
            feeitemid = "408",
            range = range,
            configProvider = { _uiState.value.ac.config },
            loadingUpdater = { it.copy(acBillRange = range, acBillsLoading = true, acBillsError = null) },
            successUpdater = { state, records ->
                state.copy(acBills = records, acBillsLoading = false, acBillsError = null)
            },
            failureUpdater = { state, message ->
                state.copy(acBillsLoading = false, acBillsError = message)
            }
        )
    }

    fun loadLightingBills(range: ElectricityBillRange = _uiState.value.lightingBillRange) {
        loadElectricityBills(
            feeitemid = "428",
            range = range,
            configProvider = { _uiState.value.lighting.config },
            loadingUpdater = { it.copy(lightingBillRange = range, lightingBillsLoading = true, lightingBillsError = null) },
            successUpdater = { state, records ->
                state.copy(lightingBills = records, lightingBillsLoading = false, lightingBillsError = null)
            },
            failureUpdater = { state, message ->
                state.copy(lightingBillsLoading = false, lightingBillsError = message)
            }
        )
    }

    private fun loadElectricityBalance(
        feeitemid: String,
        configProvider: () -> ElectricityRoomConfig,
        stateUpdater: (HomeUiState, ElectricityUiData?, String?) -> HomeUiState,
        loadingUpdater: (HomeUiState) -> HomeUiState
    ) {
        viewModelScope.launch {
            val config = configProvider()
            if (!config.isComplete) {
                _uiState.update { s -> stateUpdater(s, null, null).copy() }
                return@launch
            }
            _uiState.update(loadingUpdater)
            withContext(Dispatchers.IO) {
                ycardRepository.getElectricityBalance(
                    feeitemid = feeitemid,
                    building = config.building,
                    floor = config.floor,
                    room = config.room
                ).fold(
                    onSuccess = { data ->
                        _uiState.update { s -> stateUpdater(s, data, null) }
                    },
                    onFailure = { e ->
                        _uiState.update { s -> stateUpdater(s, null, e.message ?: "电费查询失败") }
                    }
                )
            }
        }
    }

    private fun loadElectricityBills(
        feeitemid: String,
        range: ElectricityBillRange,
        configProvider: () -> ElectricityRoomConfig,
        loadingUpdater: (HomeUiState) -> HomeUiState,
        successUpdater: (HomeUiState, List<ElectricityDailyRecord>) -> HomeUiState,
        failureUpdater: (HomeUiState, String?) -> HomeUiState
    ) {
        viewModelScope.launch {
            val config = configProvider()
            if (!config.isComplete) {
                _uiState.update { failureUpdater(it, null) }
                return@launch
            }
            _uiState.update(loadingUpdater)
            val endDate = LocalDate.now()
            withContext(Dispatchers.IO) {
                getElectricityBillsByRange(
                    feeitemid = feeitemid,
                    config = config,
                    endDate = endDate,
                    range = range
                ).fold(
                    onSuccess = { records ->
                        _uiState.update { successUpdater(it, records.sortedByDescending { record -> record.date }) }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            failureUpdater(it, e.message ?: "电费账单查询失败")
                        }
                    }
                )
            }
        }
    }

    private suspend fun getElectricityBillsByRange(
        feeitemid: String,
        config: ElectricityRoomConfig,
        endDate: LocalDate,
        range: ElectricityBillRange
    ): Result<List<ElectricityDailyRecord>> {
        if (range == ElectricityBillRange.SEVEN_DAYS) {
            return ycardRepository.getElectricityBills(
                feeitemid = feeitemid,
                building = config.building,
                floor = config.floor,
                room = config.room,
                startDate = endDate.minusDays(7).toString(),
                endDate = endDate.toString()
            )
        }

        val allRecords = mutableListOf<ElectricityDailyRecord>()
        repeat(4) { index ->
            val chunkEnd = endDate.minusDays((index * 7).toLong())
            val chunkStart = chunkEnd.minusDays(7)
            val result = ycardRepository.getElectricityBills(
                feeitemid = feeitemid,
                building = config.building,
                floor = config.floor,
                room = config.room,
                startDate = chunkStart.toString(),
                endDate = chunkEnd.toString()
            )
            result.fold(
                onSuccess = { allRecords += it },
                onFailure = { return Result.failure(it) }
            )
        }

        return Result.success(
            allRecords
                .distinctBy { it.date to it.degreeText }
                .sortedByDescending { it.date }
        )
    }


    fun onRefresh() {
        loadBalanceAndBills()
    }
}

// ── UI State ──────────────────────────────────────────────

data class HomeUiState(
    val balance: Double = 0.0,
    val timestamp: Long = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    // 账单
    val bills: List<BillRecord> = emptyList(),
    val billsLoading: Boolean = false,
    val billsError: String? = null,
    // 浴室余额
    val bathroomPhone: String = "",
    val bathroomData: BathroomBalanceData? = null,
    val bathroomLoading: Boolean = false,
    val bathroomError: String? = null,
    // 空调
    val ac: ElectricityState = ElectricityState(),
    val acBillRange: ElectricityBillRange = ElectricityBillRange.SEVEN_DAYS,
    val acBills: List<ElectricityDailyRecord> = emptyList(),
    val acBillsLoading: Boolean = false,
    val acBillsError: String? = null,
    // 照明
    val lighting: ElectricityState = ElectricityState(),
    val lightingBillRange: ElectricityBillRange = ElectricityBillRange.SEVEN_DAYS,
    val lightingBills: List<ElectricityDailyRecord> = emptyList(),
    val lightingBillsLoading: Boolean = false,
    val lightingBillsError: String? = null,
    // 网费
    val internetData: InternetBalanceData? = null,
    val internetLoading: Boolean = false,
    val internetError: String? = null,
    val internetBills: List<InternetBillRecord> = emptyList(),
    val internetBillsLoading: Boolean = false,
    val internetBillsError: String? = null,
)

enum class ElectricityBillRange(val label: String, val loadingText: String, val emptyText: String) {
    SEVEN_DAYS("近 7 天", "正在加载近 7 天明细...", "近 7 天暂无明细"),
    LAST_MONTH("近一个月", "正在分周加载近一个月明细...", "近一个月暂无明细")
}

data class ElectricityState(
    val data: ElectricityUiData? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val config: ElectricityRoomConfig = ElectricityRoomConfig()
)

/**
 * 从个人信息接口（学生一张表）提取的余额查询默认值。
 * - phone: 浴室手机号（来自基本信息）
 * - building: 楼栋中文名（来自住宿数据，仅作为 name 部分预填）
 * - room: 宿舍房间号（来自住宿数据，仅作为 room name 部分预填）
 *
 * 注意：ycard 电费 API 需要 "code&name" 格式，预填只填 name 部分，code 由用户补全。
 */
private data class BalancePrefill(
    val phone: String? = null,
    val acConfig: ElectricityRoomConfig = ElectricityRoomConfig(),
    val lightingConfig: ElectricityRoomConfig = ElectricityRoomConfig(),
)

private fun StudentInfo.toBalancePrefill(): BalancePrefill {
    val phone = firstValueOf("手机号", "手机号码", "联系电话", "移动电话")
        ?.filter(Char::isDigit)
        ?.takeIf { it.length == 11 }
    val building = firstValueOf("楼栋", "所在楼栋", "楼栋号")
    val room = firstValueOf("宿舍房间", "房间号", "寝室号")
    return BalancePrefill(
        phone = phone,
        acConfig = buildElectricityConfig(building, room, ElectricityKind.Ac),
        lightingConfig = buildElectricityConfig(building, room, ElectricityKind.Lighting)
    )
}

private fun ElectricityRoomConfig.fillMissingWith(prefill: ElectricityRoomConfig): ElectricityRoomConfig {
    if (isComplete) return this
    return ElectricityRoomConfig(
        building = building.takeIf(::isValidConfigPart) ?: prefill.building.ifBlank { building },
        floor = floor.takeIf(::isValidConfigPart) ?: prefill.floor.ifBlank { floor },
        room = room.takeIf(::isValidConfigPart) ?: prefill.room.ifBlank { room }
    )
}

private fun isValidConfigPart(value: String): Boolean {
    val parts = value.split("&", limit = 2)
    return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
}

private enum class ElectricityKind { Ac, Lighting }

private fun ElectricityRoomConfig.fillFromLegacyPrefill(kind: ElectricityKind): ElectricityRoomConfig {
    if (isComplete) return this
    val legacyBuilding = building.substringAfter("&")
        .removeSuffix("空调")
        .removeSuffix("照明")
        .takeIf { it.isNotBlank() }
    val legacyRoom = room.substringAfter("&").takeIf { it.isNotBlank() }
    return fillMissingWith(
        buildElectricityConfig(
            dormBuilding = legacyBuilding,
            dormRoom = legacyRoom,
            kind = kind
        )
    )
}

private fun buildElectricityConfig(
    dormBuilding: String?,
    dormRoom: String?,
    kind: ElectricityKind
): ElectricityRoomConfig {
    val roomNumber = dormRoom?.filter(Char::isDigit).orEmpty()
    if (roomNumber.isBlank()) return ElectricityRoomConfig()

    val room = "$roomNumber&$roomNumber"
    val floorName = floorNameFromRoom(roomNumber) ?: return ElectricityRoomConfig(room = room)
    val buildingName = electricityBuildingName(dormBuilding, roomNumber, kind)
        ?: return ElectricityRoomConfig(floor = floorName, room = room)

    val buildingCode = when (kind) {
        ElectricityKind.Ac -> acBuildingCodes[buildingName]
        ElectricityKind.Lighting -> lightingBuildingCodes[buildingName]
    }
    val floorCode = when (kind) {
        ElectricityKind.Ac -> acFloorCodes[floorName]
        ElectricityKind.Lighting -> lightingFloorCodes[floorName]
    }

    return ElectricityRoomConfig(
        building = if (buildingCode != null) "$buildingCode&$buildingName" else buildingName,
        floor = if (floorCode != null) "$floorCode&$floorName" else floorName,
        room = room
    )
}

private fun electricityBuildingName(
    dormBuilding: String?,
    roomNumber: String,
    kind: ElectricityKind
): String? {
    val base = dormBuilding?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val buildingText = if (base.contains("号楼")) {
        base
    } else {
        val buildingNo = roomNumber.firstOrNull()?.digitToIntOrNull()?.takeIf { it > 0 }
            ?: return null
        "$base${chineseDigits[buildingNo]}号楼"
    }
    return when (kind) {
        ElectricityKind.Ac -> "${buildingText}空调"
        ElectricityKind.Lighting -> "${buildingText}照明"
    }
}

private fun floorNameFromRoom(roomNumber: String): String? {
    val floorDigit = when {
        roomNumber.length >= 4 -> roomNumber.getOrNull(1)
        else -> roomNumber.firstOrNull()
    }?.digitToIntOrNull() ?: return null
    return chineseDigits[floorDigit]?.let { "${it}层" }
}

private val chineseDigits = mapOf(
    1 to "一",
    2 to "二",
    3 to "三",
    4 to "四",
    5 to "五",
    6 to "六",
    7 to "七",
    8 to "八",
    9 to "九"
)

private val acBuildingCodes = mapOf(
    "榴园二号楼空调" to "57"
)

private val lightingBuildingCodes = mapOf(
    "榴园二号楼照明" to "108"
)

private val acFloorCodes = mapOf(
    "五层" to "228"
)

private val lightingFloorCodes = mapOf(
    "五层" to "119"
)
