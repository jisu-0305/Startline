package com.example.gogoma.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gogoma.GlobalApplication
import com.example.gogoma.data.api.RetrofitInstance
import com.example.gogoma.data.api.WatchApiService
import com.example.gogoma.data.model.BooleanResponse
import com.example.gogoma.data.model.MarathonEndInitDataRequest
import com.example.gogoma.data.util.MarathonRealTimeDataUtil
import com.example.gogoma.data.util.MarathonRunService
import com.example.gogoma.data.util.UserDistanceRepository
import com.example.gogoma.utils.TokenManager
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Timer
import kotlin.concurrent.fixedRateTimer

class MarathonViewModel(application: Application) : AndroidViewModel(application),
    DataClient.OnDataChangedListener {

    private var marathonStartTimer: Timer? = null
    private var db = GlobalApplication.instance.database
    private val dataClient: DataClient = Wearable.getDataClient(application)
    private var marathonRealTimeDataUtil: MarathonRealTimeDataUtil = MarathonRealTimeDataUtil(getApplication())
    private var isMarathonRunning = true

    // 종료 변수
    private val _isEnd = MutableStateFlow(false) // 내부에서만 변경 가능, 현재 달리기 상태를 저장
    val isEnd = _isEnd.asStateFlow() // 외부에서는 읽기만 가능
    private var completedTasks = 0
    private val totalTasks = 2 // 예: 데이터 전송 2가지

    init {
        dataClient.addListener(this)
    }

    override fun onCleared() {
        dataClient.removeListener(this)
        super.onCleared()
    }

    // -------------------------------------------------------------- //
    // -----------------------[Marathon Ready]----------------------- //
    // -------------------------------------------------------------- //
    @SuppressLint("VisibleForTests")
    fun marathonReady() {
        viewModelScope.launch {
            val myInfo = db.myInfoDao().getMyInfo()
            val marathon = db.marathonDao().getMarathon()
            val friendList = db.friendDao().getAllFriends()

            myInfo!!.id = 56;
            myInfo.name = "이재훈"
            myInfo.targetPace = 800
            myInfo.runningDistance = 1000000

            if (myInfo != null && marathon != null) {
                marathonRealTimeDataUtil.setReadyData(myInfo, marathon, friendList)
                sendMarathonReady()
            }

            Log.d("marathon", "[Marathon Ready] myInfo: $myInfo, marathon: $marathon, friendList: $friendList")
        }
    }

    // -------------------------------------------------------------- //
    // -------------------[Marathon Ready to Watch]------------------ //
    // -------------------------------------------------------------- //
    @SuppressLint("VisibleForTests")
    fun sendMarathonReady() {
        val marathonRealTimeData = marathonRealTimeDataUtil.getMarathonRealTimeData()

        val putDataMapRequest = PutDataMapRequest.create("/ready").apply {
            dataMap.putLong("timestamp", System.currentTimeMillis())
            dataMap.putString("marathonTitle", marathonRealTimeData.marathonTitle)
            dataMap.putInt("totalMemberCount", marathonRealTimeData.totalMemberCount)
        }

        val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()

        dataClient.putDataItem(putDataRequest)
            .addOnSuccessListener {
                Log.d("marathon", "[Marathon Ready] 상태 전송 성공")
            }
            .addOnFailureListener { e ->
                Log.e("marathon", "[Marathon Ready] 상태 전송 실패", e)
            }

//        UserDistanceRepository.createInitialUserData(
//            marathonRealTimeData.userId,
//            onSuccess = { Log.d("MarathonRunService", "초기 데이터 생성 성공") },
//            onFailure = { exception ->
//                Log.e("MarathonRunService", "초기 데이터 생성 실패: ${exception.message}")
//            }
//        )

    }

    // -------------------------------------------------------------------------------- //
    // -----------------------[Marathon Start/End Event Listen]----------------------- //
    // ------------------------------------------------------------------------------ //
    @SuppressLint("VisibleForTests")
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                when (event.dataItem.uri.path) {
                    "/start" -> {
                        viewModelScope.launch {
                            val myInfo = db.myInfoDao().getMyInfo()
                            if (myInfo != null) {
                                val intent = Intent(getApplication(), MarathonRunService::class.java).apply {
                                    putExtra("userId", myInfo.id)
                                }
                                getApplication<Application>().startForegroundService(intent)
                            } else {
                                Log.e("marathon", "myInfo null")
                            }
                        }
                        startMarathonSendData()
                        Log.d("marathon", "[Marathon Start] 워치로부터 마라톤 시작 신호 도착")
                    }
//                    "/end" -> {
//                        // 서비스 종료
//                        val intent = Intent(getApplication(), MarathonRunService::class.java)
//                        getApplication<Application>().stopService(intent)
//
//                        marathonRealTimeDataUtil.endUpdating()
//                        stopMarathonSendData()
//                        isMarathonRunning = false
//                        Log.d("marathon", "[Marathon End] 워치로부터 마라톤 종료 신호 도착")
//                    }
                }
            }
        }
    }

    private fun startMarathonSendData() {
        marathonStartTimer = fixedRateTimer(
            name = "MarathonDataSender",
            daemon = true,
            initialDelay = 0L,
            period = 1000L
        ) {
            var marathonRealTimeData = marathonRealTimeDataUtil.getMarathonRealTimeData()

            if (marathonRealTimeData.currentDistance >= marathonRealTimeData.totalDistance) {
                marathonRealTimeDataUtil.updateData()
                marathonSendData()

                val intent = Intent(getApplication(), MarathonRunService::class.java)
                getApplication<Application>().stopService(intent)
                marathonRealTimeDataUtil.endUpdating()
                stopMarathonSendData()
                isMarathonRunning = false
            } else {
                marathonRealTimeDataUtil.updateData()
                marathonSendData()
            }
        }
    }


    @SuppressLint("VisibleForTests")
    private fun stopMarathonSendData() {
        marathonStartTimer?.cancel()
        marathonStartTimer = null

        val putDataMapRequest = PutDataMapRequest.create("/end").apply {
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()

        dataClient.putDataItem(putDataRequest)
            .addOnSuccessListener {
                // 데이터 전송 성공 로그
                onTaskCompleted()
            }
            .addOnFailureListener { e ->
                // 데이터 전송 실패 로그
            }

        val marathonRealTimeData = marathonRealTimeDataUtil.getMarathonRealTimeData()
        val marathonId = marathonRealTimeData.marathonId

        val request = MarathonEndInitDataRequest(
            currentPace = marathonRealTimeData.currentPace,
            runningTime = marathonRealTimeData.currentTime,
            totalMemberCount = marathonRealTimeData.totalMemberCount,
            myRank = marathonRealTimeData.myRank
        )

        val aceessToken = TokenManager.getAccessToken(getApplication())

        if (!aceessToken.isNullOrEmpty()) {
            var apiService = RetrofitInstance.watchApiService
            val call = apiService.updateMarathonEndData(aceessToken, marathonId, request)

            call.enqueue(object : Callback<BooleanResponse> {
                override fun onResponse(
                    call: Call<BooleanResponse>,
                    response: Response<BooleanResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.d("marathon", "마라톤 종료 신호를 서버로 전송했습니다.")
                        onTaskCompleted()
                    } else {
                        Log.d("marathon", "마라톤 종료 신호를 서버로 전송하지못했습니다.")
                    }
                }

                override fun onFailure(call: Call<BooleanResponse>, t: Throwable) {
                    Log.d("marathon", "마라톤 종료 신호 호출을 실패했습니다.")
                }
            })
            Log.d("MarathonRunService", "[Marathon End] 데이터 전송 및 위치 추적 중단")
        }
    }

    private fun onTaskCompleted() {
        completedTasks++
        if (completedTasks >= totalTasks) {
            _isEnd.value = true // 모든 작업 완료 시 상태 업데이트
            Log.d("MarathonViewModel", "모든 작업 완료, 마라톤 종료 상태 업데이트")
        }
    }

    fun resetMarathonStopState(isEnd: Boolean) {
        _isEnd.value = isEnd
        completedTasks = 0
    }

    // -------------------------------------------------------------------------------- //
    // ----------------------[Marathon Ing - Send Data To Watch]---------------------- //
    // ------------------------------------------------------------------------------ //
    @SuppressLint("VisibleForTests")
    fun marathonSendData() {
        if(!isMarathonRunning) {
            Log.d("MarathonRunService", "[Marathon End] 마라톤이 종료되어 데이터 전송 중단")
            return
        }
        val marathonRealTimeData = marathonRealTimeDataUtil.getMarathonRealTimeData()
        Log.d("marathon", "[Marathon Ing] 데이터 전송 성공 : $marathonRealTimeData")

        val putDataMapRequest = PutDataMapRequest.create("/update").apply {
            dataMap.putInt("totalMemberCount", marathonRealTimeData.totalMemberCount)
            dataMap.putInt("totalDistance", marathonRealTimeData.totalDistance)
            dataMap.putInt("currentDistance", marathonRealTimeData.currentDistance)
            dataMap.putFloat("currentDistanceRate", marathonRealTimeData.currentDistanceRate)
            dataMap.putInt("targetPace", marathonRealTimeData.targetPace)
            dataMap.putInt("currentPace", marathonRealTimeData.currentPace)
            dataMap.putInt("targetTime", marathonRealTimeData.targetTime)
            dataMap.putInt("currentTime", marathonRealTimeData.currentTime)
            dataMap.putInt("myRank", marathonRealTimeData.myRank)
            dataMap.putString("state", marathonRealTimeData.state)

            val jsonFriendInfoList = Gson().toJson(marathonRealTimeData.friendInfoList)
            dataMap.putString("friendInfoList", jsonFriendInfoList)
        }
        val putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent()

        dataClient.putDataItem(putDataRequest)
            .addOnSuccessListener {
                // 데이터 전송 성공 로그
            }
            .addOnFailureListener { e ->
                // 데이터 전송 실패 로그
            }
    }
}
