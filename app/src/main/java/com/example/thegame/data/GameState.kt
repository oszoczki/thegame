package com.example.thegame.data

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class GameState(
    var gameId: String = "",
    var players: MutableMap<String, Player> = mutableMapOf(),
    var piles: MutableMap<String, Int> = mutableMapOf("1" to 1, "2" to 1, "3" to 100, "4" to 100),
    var deck: MutableList<Int> = mutableListOf(),
    var playerTurnOrder: MutableList<String> = mutableListOf(),
    var currentPlayerId: String = "",
    var lastPlayedCardsCount: Int = 0,
    var lastPlayedPileIndex: String? = null, // To highlight the last played pile
    var isGameStarted: Boolean = false,
    var isGameOver: Boolean = false,
    var winnerPlayerId: String? = null
) {
    // No-arg constructor for Firebase
    constructor() : this("", mutableMapOf(), mutableMapOf("1" to 1, "2" to 1, "3" to 100, "4" to 100), mutableListOf(), mutableListOf())
}
