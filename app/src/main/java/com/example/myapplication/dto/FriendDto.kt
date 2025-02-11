package com.example.myapplication.dto

data class FriendDto(
    var id: Int = 0,
    var userId: Int = 0,
    var friendId: Int = 0,
    var friendName: String? = null
)