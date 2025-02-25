package com.example.gogoma.data.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlin.math.roundToInt

class MarathonRunService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private var cumulativeDistance: Int = 0
    private var previousLocation: Location? = null

    private var currentUserId: Int = 0

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationRequest()
        setupLocationCallback()
        startForegroundServiceWithNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentUserId = intent?.getIntExtra("userId", 0) ?: 0

//        UserDistanceRepository.createInitialUserData(
//            currentUserId,
//            onSuccess = { Log.d("MarathonRunService", "초기 데이터 생성 성공") },
//            onFailure = { exception ->
//                Log.e("MarathonRunService", "초기 데이터 생성 실패: ${exception.message}")
//            }
//        )
//
//        cumulativeDistance = 0
//        previousLocation = null

        startLocationUpdates()

        return START_STICKY
    }

    private fun setupLocationRequest() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000)
            .build()
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            @SuppressLint("DefaultLocale")
            override fun onLocationResult(locationResult: LocationResult) {
                for (currentLocation in locationResult.locations) {
                    Log.d("MarathonRunService", "현재 위치 -> 위도: ${currentLocation.latitude}, 경도: ${currentLocation.longitude}")
                    previousLocation?.let { prevLoc ->
                        // 이전 위치와 현재 위치 간 거리를 구한 후, cm 단위로 변환
                        val distanceInCm = (prevLoc.distanceTo(currentLocation) * 100).roundToInt()
                        cumulativeDistance += distanceInCm

                        // 화면 표시용: 누적 거리와 이번 이동 거리를 km 단위로 변환 (1km = 100000cm)
                        val formattedCumulative = String.format("%.2f", cumulativeDistance / 100000.0)
                        val formattedIncrement = String.format("%.2f", distanceInCm / 100000.0)
                        Log.d("MarathonRunService", "이번 이동 거리: $formattedIncrement km, 누적 거리: $formattedCumulative km")

                        // DB 업데이트: 누적 거리를 cm 단위로 업데이트
//                        UserDistanceRepository.updateUserCumulativeDistance(
//                            currentUserId,
//                            cumulativeDistance,
//                            onSuccess = { Log.d("MarathonRunService", "Firebase 업데이트 성공: $cumulativeDistance cm") },
//                            onFailure = { exception ->
//                                Log.e("MarathonRunService", "Firebase 업데이트 실패: ${exception.message}")
//                            }
//                        )
                    }
                    if (previousLocation == null) {
                        Log.d("MarathonRunService", "첫 위치 업데이트입니다.")
                    }
                    previousLocation = currentLocation
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("MarathonRunService", "위치 권한 부족")
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun startForegroundServiceWithNotification() {
        val channelId = "MarathonRunServiceChannel"
        val channel = NotificationChannel(
            channelId,
            "마라톤 실행 서비스",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("마라톤 실행중")
            .setContentText("누적 거리 업데이트 중입니다.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
