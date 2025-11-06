package com.example.thegame.game

import com.example.thegame.data.Card
import com.example.thegame.data.GameState
import com.example.thegame.data.Player

object GameLogic {

    const val INITIAL_HAND_SIZE_2_PLAYERS = 7
    const val INITIAL_HAND_SIZE_3_5_PLAYERS = 6
    const val MIN_CARDS_TO_PLAY_PER_TURN = 2 // Alap szabály
    const val MIN_CARDS_TO_PLAY_PER_TURN_EMPTY_DECK = 1

    fun calculateInitialHandSize(numPlayers: Int): Int {
        return if (numPlayers <= 2) INITIAL_HAND_SIZE_2_PLAYERS else INITIAL_HAND_SIZE_3_5_PLAYERS
    }

    fun isValidMove(card: Card, pileIndex: String, currentPiles: Map<String, Int>): Boolean {
        val topCard = currentPiles[pileIndex] ?: return false

        return when (pileIndex) {
            "1", "2" -> { // Emelkedő sorok (1 -> 99)
                card.number > topCard || card.number == topCard - 10 // Normál vagy 10-es visszalépés
            }
            "3", "4" -> { // Ereszkedő sorok (100 -> 2)
                card.number < topCard || card.number == topCard + 10 // Normál vagy 10-es visszalépés
            }
            else -> false
        }
    }

    fun createInitialGameState(gameId: String, players: MutableMap<String, Player>, turnOrder: MutableList<String>): GameState {
        val fullDeck = (2..99).toMutableList().shuffled().toMutableList()
        val handSize = calculateInitialHandSize(players.size)

        val updatedPlayers = players.values.map { player ->
            val hand = fullDeck.take(handSize)
            fullDeck.removeAll(hand)
            player.copy(hand = hand.sorted().toMutableList())
        }.associateBy { it.id }.toMutableMap()

        return GameState(
            gameId = gameId,
            players = updatedPlayers,
            piles = mutableMapOf("1" to 1, "2" to 1, "3" to 100, "4" to 100),
            deck = fullDeck,
            playerTurnOrder = turnOrder,
            currentPlayerId = turnOrder.first(),
            isGameStarted = true
        )
    }

    fun checkGameOver(gameState: GameState, cardsPlayedThisTurn: Int, isEndOfTurn: Boolean): GameState {
        // Win Condition is always checked.
        if (gameState.deck.isEmpty() && gameState.players.values.all { it.hand.isEmpty() }) {
            return gameState.copy(isGameOver = true, winnerPlayerId = "all")
        }

        val currentPlayer = gameState.players[gameState.currentPlayerId] ?: return gameState
        val hasValidMove = currentPlayer.hand.any { cardNum ->
            val card = Card(cardNum)
            gameState.piles.keys.any { pileIndex -> isValidMove(card, pileIndex, gameState.piles) }
        }

        if (isEndOfTurn) {
            // At the end of a turn, the currentPlayer in gameState is the NEXT player.
            // If that player has no cards or no valid moves, it's a loss.
            if (currentPlayer.hand.isNotEmpty() && !hasValidMove) {
                return gameState.copy(isGameOver = true, winnerPlayerId = null)
            }
        } else {
            // In the middle of a turn, check if the player is stuck AND cannot legally end their turn.
            val canEndTurn = if (gameState.deck.isEmpty()) {
                cardsPlayedThisTurn >= 1
            } else {
                cardsPlayedThisTurn >= MIN_CARDS_TO_PLAY_PER_TURN
            }

            if (!canEndTurn && currentPlayer.hand.isNotEmpty() && !hasValidMove) {
                return gameState.copy(isGameOver = true, winnerPlayerId = null)
            }
        }

        return gameState // Game is not over
    }
}
