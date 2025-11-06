package com.example.thegame.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Player(
    var id: String = "",
    var name: String = "",
    var hand: MutableList<Int> = mutableListOf(), // Kártyaszámok listája
    var isHost: Boolean = false
)
