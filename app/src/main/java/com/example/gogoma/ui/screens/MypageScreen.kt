package com.example.gogoma.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.gogoma.ui.components.BottomBar
import com.example.gogoma.ui.components.TopBarArrow
import com.example.gogoma.viewmodel.UserViewModel

@Composable
fun MypageScreen(navController: NavController, userViewModel: UserViewModel) {
    Scaffold (
        topBar = { TopBarArrow (
            title = "마이 페이지",
            onBackClick = { navController.popBackStack() }
        )
        },
        bottomBar = { BottomBar(navController = navController, userViewModel) }
    ){ paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)){
            Text("마이페이지입니다")
        }
    }
}