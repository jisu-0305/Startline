package com.example.gogoma.data.util

import android.content.Context
import android.util.Log
import com.example.gogoma.data.dto.FriendDto
import com.example.gogoma.data.dto.MarathonRealTimeData

import com.example.gogoma.data.roomdb.entity.Friend
import com.example.gogoma.data.roomdb.entity.Marathon
import com.example.gogoma.data.roomdb.entity.MyInfo
import java.util.Timer
import java.util.concurrent.atomic.AtomicInteger

class MarathonRealTimeDataUtil(private val context: Context) {

    private var timer: Timer? = null

    private var marathonRealTimeData = MarathonRealTimeData (
        // 사용자 및 마라톤 정보
        userId = 0,
        userName = "",
        marathonId = 0,
        marathonTitle = "",

        // 거리 관련 정보 (단위: cm)
        totalDistance = 0,
        currentDistance = 0,
        currentDistanceRate = 0.0f,

        // 페이스 및 시간 관련 (단위: 초)
        targetPace = 0,   // 예: 600페이스 -> 1km 360초 페이스
        currentPace = 0,  // 예: 700페이스 -> 1km 420초 페이스
        targetTime = 0,
        currentTime = 0,

        // 상태 및 순위 정보
        state = "G",      // "Green : 목표 페이스 이상", "Yellow : 목표 페이스 1분 초과까지", "Red : 그 외"
        myRank = 0,
        totalMemberCount = 0,

        // 친구 관련 목록
        friendList = listOf(),
        friendInfoList = listOf()
    )

    fun setReadyData(myInfo: MyInfo, marathon: Marathon, friendList: List<Friend>) {
        marathonRealTimeData.apply {

            // 거리 및 페이스 관련 값 설정
            totalDistance = myInfo.runningDistance

            // targetPace 계산
            val minutes = myInfo.targetPace / 100
            val seconds = myInfo.targetPace % 100
            targetPace = minutes * 60 + seconds

            // targetTime 계산
            targetTime = (myInfo.runningDistance * targetPace) / 100000

            // 사용자 및 마라톤 정보 설정
            userId = myInfo.id
            userName = myInfo.name
            marathonId = marathon.id
            marathonTitle = marathon.title

            // 참여 멤버 수 및 친구 목록 설정
            totalMemberCount = friendList.size + 1
            this.friendList = friendList

            // 친구 정보 리스트 생성 (친구 목록 + 본인)
            friendInfoList = friendList.map { friend ->
                FriendDto(
                    userId = friend.id,
                    friendName = friend.name,
                    currentDistance = 0,      // 초기값
                    isMe = friend.id == myInfo.id,
                    rank = 0,
                    currentDistanceRate = 0.0f,
                    gapDistance = 0
                )
            }
//            + FriendDto(
//                userId = myInfo.id,
//                friendName = myInfo.name,
//                currentDistance = 0,
//                isMe = true,
//                rank = 0,
//                currentDistanceRate = 0.0f,
//                gapDistance = 0
//            )
        }

        Log.d("marathon", "[Marathon Ready] MarathonRealTimeData: $marathonRealTimeData")
    }

    fun getMarathonRealTimeData(): MarathonRealTimeData {
        return marathonRealTimeData
    }
 
    fun updateData() {
        marathonRealTimeData.currentTime += 1
        marathonRealTimeData.currentDistance += 1
        marathonRealTimeData.currentPace = marathonRealTimeData.currentTime * 100000 / marathonRealTimeData.currentDistance

        if (marathonRealTimeData.currentDistance < 1000) {
            marathonRealTimeData.currentPace = 0
        }

        if (marathonRealTimeData.currentPace <= marathonRealTimeData.targetPace) {
            marathonRealTimeData.state = "G"
        } else if (marathonRealTimeData.currentPace <= marathonRealTimeData.targetPace + 10) {
            marathonRealTimeData.state = "Y"
        } else {
            marathonRealTimeData.state = "R"
        }

        UserDistanceRepository.getUserCumulativeDistance(userId = marathonRealTimeData.userId) { distance ->
            if (distance != null) {
                marathonRealTimeData.currentDistance = distance
                marathonRealTimeData.currentDistanceRate =
                    if (marathonRealTimeData.totalDistance != 0)
                        distance.toFloat() / marathonRealTimeData.totalDistance
                    else 0.0f

                // 현재 페이스 계산: km 당 걸린 초
                // currentDistance는 cm 단위이므로 km = currentDistance / 100000.0
                marathonRealTimeData.currentPace =
                    if (marathonRealTimeData.currentDistance > 0)
                        (marathonRealTimeData.currentTime / (marathonRealTimeData.currentDistance.toFloat() / 100000))
                            .toInt()
                    else 0

            } else {
                Log.d("marathon", "누적 거리 값을 가져오지 못했습니다.")
            }
        }

        updateList()

        Log.d("marathon", "업데이트된 MarathonRealTimeData: $marathonRealTimeData")
    }

    fun endUpdating() {
        timer?.cancel()
        timer = null
    }

    fun updateList() {
        val updatedList = mutableListOf<FriendDto>()
        val totalFriends = marathonRealTimeData.friendInfoList.size
        val processedCount = AtomicInteger(0)

        marathonRealTimeData.friendInfoList.forEach { friend ->
            UserDistanceRepository.getUserCumulativeDistance(userId = friend.userId) { distance ->
                processedCount.incrementAndGet()
                if (distance != null) {
                    updatedList.add(
                        FriendDto(
                            userId = friend.userId,
                            friendName = friend.friendName,
                            currentDistance = distance,
                            isMe = friend.userId == marathonRealTimeData.userId,
                            rank = 0,
                            currentDistanceRate = if (marathonRealTimeData.totalDistance != 0)
                                distance.toFloat() / marathonRealTimeData.totalDistance
                            else 0.0f,

                            gapDistance = if (friend.userId != marathonRealTimeData.userId)
                                distance - marathonRealTimeData.currentDistance
                            else 0
                        )
                    )
                } else {
                    Log.d("marathon", "userId=${friend.userId}의 누적 거리를 가져오지 못했습니다.")
                }

                if (processedCount.get() == totalFriends) {
                    updatedList.sortByDescending { it.currentDistance }

                    updatedList.forEachIndexed { index, friendDto ->
                        friendDto.rank = index + 1

                        if (friendDto.userId == marathonRealTimeData.userId) {
                            marathonRealTimeData.myRank = friendDto.rank
                        }
                    }

                    marathonRealTimeData.friendInfoList = updatedList
                }
            }
        }
    }

}

