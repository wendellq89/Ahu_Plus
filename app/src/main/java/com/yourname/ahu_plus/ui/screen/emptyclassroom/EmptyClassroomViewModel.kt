package com.yourname.ahu_plus.ui.screen.emptyclassroom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.ahu_plus.data.GsonProvider
import com.yourname.ahu_plus.data.local.SessionManager
import com.yourname.ahu_plus.data.model.AhuUnitTimes
import com.yourname.ahu_plus.data.model.BuildingInfo
import com.yourname.ahu_plus.data.model.CampusBuildingData
import com.yourname.ahu_plus.data.model.CampusInfo
import com.yourname.ahu_plus.data.repository.EmptyClassroomRepository
import com.yourname.ahu_plus.data.repository.FreeRoomResult
import com.yourname.ahu_plus.data.repository.JwAuthRepository
import com.yourname.ahu_plus.data.repository.SessionExpiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class EmptyClassroomViewModel(
    private val jwAuthRepository: JwAuthRepository,
    private val emptyClassroomRepository: EmptyClassroomRepository,
    private val sessionManager: SessionManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EmptyClassroomUiState())
    val uiState: StateFlow<EmptyClassroomUiState> = _uiState.asStateFlow()

    private val gson = GsonProvider.instance

    init {
        viewModelScope.launch {
            val restored = restoreFromCache()
            if (!restored) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentUnit = AhuUnitTimes.getCurrentUnit(LocalTime.now())
                    )
                }
            }
        }
    }

    fun selectCampus(campusId: String) {
        val buildings = CampusBuildingData.getBuildings(campusId)
        _uiState.update {
            it.copy(
                selectedCampusId = campusId,
                selectedBuildingId = null,
                selectedFloor = null,
                buildings = buildings,
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = emptyList(),
                error = null
            )
        }
    }

    fun selectBuilding(buildingId: String) {
        _uiState.update {
            it.copy(
                selectedBuildingId = buildingId,
                selectedFloor = null,
                rooms = emptyList(),
                filteredRooms = emptyList(),
                availableFloors = emptyList()
            )
        }
        viewModelScope.launch { loadData(isRefresh = false) }
    }

    fun selectFloor(floor: Int?) {
        _uiState.update { state ->
            val filtered = if (floor == null) state.rooms
            else state.rooms.filter { it.room.floor == floor }
            state.copy(selectedFloor = floor, filteredRooms = filtered)
        }
    }

    fun onRefresh() {
        viewModelScope.launch { loadData(isRefresh = false) }
    }

    private suspend fun restoreFromCache(): Boolean {
        val sm = sessionManager ?: return false
        val json = sm.getEmptyClassroomJson() ?: return false
        val key = sm.getEmptyClassroomKey() ?: return false
        val updatedAt = sm.getEmptyClassroomUpdatedAt()
        val today = LocalDate.now().toString()

        if (!key.startsWith(today)) return false
        if (System.currentTimeMillis() - updatedAt > 5 * 60 * 1000) return false

        val parts = key.split("|")
        if (parts.size < 3) return false
        val buildingId = parts[1]
        val campusId = parts[2]

        return try {
            withContext(Dispatchers.IO) {
                val rooms = gson.fromJson(json, Array<FreeRoomResult>::class.java).toList()
                val campus = CampusBuildingData.campuses.find { it.id == campusId }
                val floors = rooms.mapNotNull { it.room.floor }.distinct().sorted()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedCampusId = campusId,
                        selectedBuildingId = buildingId,
                        buildings = campus?.buildings ?: emptyList(),
                        rooms = rooms,
                        filteredRooms = rooms,
                        availableFloors = floors,
                        currentUnit = AhuUnitTimes.getCurrentUnit(LocalTime.now()),
                        error = null,
                        needsLogin = false
                    )
                }
            }
            true
        } catch (_: Exception) { false }
    }

    private suspend fun loadData(isRefresh: Boolean) {
        val buildingId = _uiState.value.selectedBuildingId ?: return
        val campusId = _uiState.value.selectedCampusId ?: return
        val currentUnit = AhuUnitTimes.getCurrentUnit(LocalTime.now())

        if (currentUnit == null) {
            _uiState.update { it.copy(isLoading = false, rooms = emptyList(), filteredRooms = emptyList()) }
            return
        }

        val wasLoaded = _uiState.value.rooms.isNotEmpty()
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null, currentUnit = currentUnit) }
        } else {
            _uiState.update { it.copy(currentUnit = currentUnit) }
        }

        try {
            withContext(Dispatchers.IO) {
                val authResult = jwAuthRepository.authenticate()
                if (authResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsLogin = !wasLoaded,
                            error = if (!wasLoaded) "教务处认证失败" else null
                        )
                    }
                    return@withContext
                }

                emptyClassroomRepository.getFreeRoomsWithDuration(
                    buildingId = buildingId,
                    campusId = campusId,
                    currentUnit = currentUnit
                ).fold(
                    onSuccess = { rooms ->
                        val sm = sessionManager
                        if (sm != null) {
                            try {
                                val cacheKey = "${LocalDate.now()}|$buildingId|$campusId"
                                sm.saveEmptyClassroomJson(gson.toJson(rooms), cacheKey)
                            } catch (_: Exception) {}
                        }
                        val floors = rooms.mapNotNull { it.room.floor }.distinct().sorted()
                        val selectedFloor = _uiState.value.selectedFloor
                        val filtered = if (selectedFloor != null)
                            rooms.filter { it.room.floor == selectedFloor } else rooms
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                rooms = rooms,
                                filteredRooms = filtered,
                                availableFloors = floors,
                                error = null,
                                needsLogin = false
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = if (!wasLoaded) (e.message ?: "空教室查询失败") else it.error,
                                needsLogin = !wasLoaded && e is SessionExpiredException
                            )
                        }
                    }
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = if (!wasLoaded) "未知错误: ${e.message}" else it.error,
                    needsLogin = !wasLoaded && e is SessionExpiredException
                )
            }
        }
    }
}

data class EmptyClassroomUiState(
    val isLoading: Boolean = false,
    val selectedCampusId: String? = null,
    val selectedBuildingId: String? = null,
    val selectedFloor: Int? = null,
    val buildings: List<BuildingInfo> = emptyList(),
    val rooms: List<FreeRoomResult> = emptyList(),
    val filteredRooms: List<FreeRoomResult> = emptyList(),
    val availableFloors: List<Int> = emptyList(),
    val currentUnit: Int? = null,
    val error: String? = null,
    val needsLogin: Boolean = false
) {
    val availableCampuses: List<CampusInfo>
        get() = CampusBuildingData.campuses

    val hasBuildingSelected: Boolean
        get() = selectedCampusId != null && selectedBuildingId != null
}
