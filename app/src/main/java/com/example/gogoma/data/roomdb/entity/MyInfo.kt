package com.example.gogoma.data.roomdb.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class MyInfo(
    @PrimaryKey(autoGenerate = false)
    var id: Int,
    var name: String,
    var targetPace: Int,
    var runningDistance: Int
)
