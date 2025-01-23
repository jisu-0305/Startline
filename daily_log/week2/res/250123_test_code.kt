package com.example.progressbartest.presentation

import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.progressbartest.R
import com.example.progressbartest.presentation.theme.ProgressBarTestTheme
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    ProgressBarTestTheme {
        var progress by remember { mutableStateOf(0f) } // Progress percentage
        var distance by remember { mutableStateOf(0) } // Distance in meters
        var totalDistance = 1000 // Total distance (1km)
        var totalTime = 20000L // Total time in milliseconds (20 seconds)

        // Animated progress for smoother transitions
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 500) // Adjust animation duration
        )

        val animatedDistance by animateFloatAsState(
            targetValue = distance.toFloat(),
            animationSpec = tween(durationMillis = 500)
        )

        // Start the countdown timer
        LaunchedEffect(Unit) {
            object : CountDownTimer(totalTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    // Increment distance by a random value (between 20 and 100 meters)
                    val randomIncrement = Random.nextInt(20, 101)
                    distance += randomIncrement // Random increment in meters
                    progress = distance / totalDistance.toFloat() // Calculate progress
                }

                override fun onFinish() {
                    distance = totalDistance
                    progress = 1f
                }
            }.start()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            // Circular Progress Bar
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(MaterialTheme.colors.background), // Ensuring the background is the same as the app background
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = animatedProgress,
                    modifier = Modifier.size(200.dp),
                    strokeWidth = 10.dp
                )

                // Distance text centered inside the progress bar
                Text(
                    text = "${animatedDistance.toInt()}m",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Additional Info (Rank, etc.)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Spacer(modifier = Modifier.height(250.dp)) // Adjusting for text positioning below the progress bar

                // Rank Display
                Text(
                    text = "현재 등수: 1등",
                    style = MaterialTheme.typography.body1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp()
}