package com.example.thegame.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.thegame.data.Card
import com.example.thegame.data.GameState
import com.example.thegame.data.Player
import com.example.thegame.game.GameLogic
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    private val authStateListener: FirebaseAuth.AuthStateListener
    private var gameValueEventListener: ValueEventListener? = null
    private var currentGameRef: DatabaseReference? = null

    private val _gameState = MutableStateFlow(GameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _isUserAuthenticated = MutableStateFlow(auth.currentUser != null)
    val isUserAuthenticated: StateFlow<Boolean> = _isUserAuthenticated.asStateFlow()

    val currentUserId: String get() = auth.currentUser?.uid ?: "anonymous"

    private val _selectedCard = mutableStateOf<Card?>(null)
    val selectedCard: State<Card?> = _selectedCard

    private var cardsPlayedThisTurn: Int = 0

    init {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            _isUserAuthenticated.value = firebaseAuth.currentUser != null
        }
        auth.addAuthStateListener(authStateListener)

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnFailureListener {
                    println("Hiba az anonim bejelentkezéskor: ${it.message}")
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        leaveLobby()
        detachListener()
    }
    
    fun leaveLobby() {
        val gameId = _gameState.value.gameId
        val leavingPlayerId = currentUserId
        if (gameId.isEmpty()) {
            return
        }

        val gameRef = database.child("games").child(gameId)

        gameRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val gameState = currentData.getValue(GameState::class.java)
                if (gameState?.players?.containsKey(leavingPlayerId) != true) {
                    return Transaction.success(currentData)
                }

                val wasHost = gameState.players[leavingPlayerId]?.isHost == true
                val wasCurrentPlayer = gameState.currentPlayerId == leavingPlayerId
                val leaverIndex = gameState.playerTurnOrder.indexOf(leavingPlayerId)

                // Remove player
                gameState.players.remove(leavingPlayerId)
                gameState.playerTurnOrder.remove(leavingPlayerId)

                if (gameState.players.isEmpty()) {
                    currentData.value = null
                } else {
                    if (wasHost) {
                        val newHostId = if (gameState.playerTurnOrder.isNotEmpty()) {
                            gameState.playerTurnOrder.first()
                        } else {
                            gameState.players.keys.sorted().first()
                        }
                        gameState.players[newHostId] = gameState.players[newHostId]!!.copy(isHost = true)
                        if (!gameState.isGameStarted) {
                            gameState.currentPlayerId = newHostId
                        }
                    }

                    if (gameState.isGameStarted && wasCurrentPlayer && gameState.playerTurnOrder.isNotEmpty()) {
                        val nextPlayerIndex = if (leaverIndex != -1) {
                            leaverIndex % gameState.playerTurnOrder.size
                        } else {
                            0
                        }
                        gameState.currentPlayerId = gameState.playerTurnOrder[nextPlayerIndex]
                    }

                    if (gameState.isGameStarted && gameState.players.size < 2) {
                        gameState.isGameOver = true
                        gameState.winnerPlayerId = if (gameState.players.size == 1) gameState.players.keys.first() else null
                    }

                    currentData.value = gameState
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    println("Firebase transaction failed while leaving lobby: ${error.message}")
                }
                detachListener()
                _gameState.value = GameState()
            }
        })
    }

    private fun detachListener() {
        gameValueEventListener?.let { currentGameRef?.removeEventListener(it) }
        gameValueEventListener = null
        currentGameRef = null
    }

    fun resetGame() {
        val gameId = _gameState.value.gameId

        // Detach listener and reset local state immediately for instant UI feedback
        detachListener()
        _gameState.value = GameState()

        if (gameId.isEmpty()) {
            return
        }

        val gameRef = database.child("games").child(gameId)

        // Use a transaction for safe, atomic removal and cleanup
        gameRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val gameState = currentData.getValue(GameState::class.java)
                if (gameState == null) {
                    // The game might have been already deleted. Success.
                    return Transaction.success(currentData)
                }

                // Remove the current player from the map
                gameState.players.remove(currentUserId)

                // If no players are left, delete the game by setting its value to null
                if (gameState.players.isEmpty()) {
                    currentData.value = null
                } else {
                    // Otherwise, just update the game state with the removed player
                    currentData.value = gameState
                }
                return Transaction.success(currentData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    println("Firebase transaction failed to remove player: ${error.message}")
                }
            }
        })
    }

    fun returnToLobby() {
        val currentGameState = _gameState.value
        if (currentGameState.gameId.isEmpty()) return

        val hostId = currentGameState.players.entries.find { it.value.isHost }?.key ?: currentUserId

        // Reset players' hands
        val playersInLobby = currentGameState.players.mapValues {
            it.value.copy(hand = mutableListOf())
        }.toMutableMap()

        val lobbyState = GameState(
            gameId = currentGameState.gameId,
            players = playersInLobby,
            piles = mutableMapOf("1" to 1, "2" to 1, "3" to 100, "4" to 100),
            deck = mutableListOf(),
            playerTurnOrder = mutableListOf(),
            currentPlayerId = hostId, // Set host as current player in lobby
            isGameStarted = false,
            isGameOver = false,
            winnerPlayerId = null
        )
        
        // Only the host should push the reset state
        if (currentUserId == hostId) {
            database.child("games").child(lobbyState.gameId).setValue(lobbyState)
        }
        // All clients will get the update via the listener and navigate back to the lobby.
    }

    fun createGame() {
        if (auth.currentUser == null) return
        detachListener() // Ensure no old listeners are active
        viewModelScope.launch {
            val gameId = (100000..999999).random().toString()
            val hostPlayer = Player(currentUserId, "Játékos ${currentUserId.substring(0, 4)}", isHost = true)
            val initialGameState = GameState(
                gameId = gameId,
                players = mutableMapOf(currentUserId to hostPlayer),
                currentPlayerId = currentUserId,
                isGameStarted = false
            )
            _gameState.value = initialGameState
            database.child("games").child(gameId).setValue(initialGameState).addOnSuccessListener {
                listenToGameChanges(gameId)
            }
        }
    }

    fun joinGame(gameId: String) {
        if (auth.currentUser == null) return
        detachListener() // Ensure no old listeners are active
        listenToGameChanges(gameId)
        database.child("games").child(gameId).get().addOnSuccessListener { snapshot ->
            val currentGameState = parseGameState(snapshot)
            if (currentGameState != null && !currentGameState.isGameStarted) {
                val newPlayer = Player(currentUserId, "Játékos ${currentUserId.substring(0, 4)}")
                val updatedPlayers = currentGameState.players.toMutableMap()
                updatedPlayers[currentUserId] = newPlayer
                val newGameState = currentGameState.copy(players = updatedPlayers)
                database.child("games").child(gameId).setValue(newGameState)
            }
        }.addOnFailureListener {
            println("Nem sikerült csatlakozni a játékhoz: ${it.message}")
        }
    }

    fun setPlayerName(newName: String) {
        if (currentUserId.isEmpty()) return

        val currentGameState = _gameState.value
        val currentPlayer = currentGameState.players[currentUserId] ?: return

        val updatedPlayer = currentPlayer.copy(name = newName)
        val updatedPlayers = currentGameState.players.toMutableMap()
        updatedPlayers[currentUserId] = updatedPlayer

        val newGameState = currentGameState.copy(players = updatedPlayers)
        database.child("games").child(currentGameState.gameId).child("players").setValue(newGameState.players)
    }

    fun startGame() {
        if (currentUserId != _gameState.value.players.values.find { it.isHost }?.id) {
            println("Csak a host indíthatja a játékot!")
            return
        }
        val currentPlayers = _gameState.value.players
        if (currentPlayers.size < 2) {
            println("Legalább két játékos kell az indításhoz!")
            return
        }
        val turnOrder = currentPlayers.keys.sorted().toMutableList()
        val initialGameState = GameLogic.createInitialGameState(_gameState.value.gameId, currentPlayers, turnOrder)
        database.child("games").child(_gameState.value.gameId).setValue(initialGameState)
    }

    private fun parseGameState(snapshot: DataSnapshot): GameState? {
        try {
            if (!snapshot.exists()) {
                return null
            }

            val state = GameState()
            state.gameId = snapshot.child("gameId").getValue(String::class.java) ?: ""
            state.currentPlayerId = snapshot.child("currentPlayerId").getValue(String::class.java) ?: ""
            state.lastPlayedCardsCount = snapshot.child("lastPlayedCardsCount").getValue(Int::class.java) ?: 0
            state.lastPlayedPileIndex = snapshot.child("lastPlayedPileIndex").getValue(String::class.java)
            state.isGameStarted = snapshot.child("gameStarted").getValue(Boolean::class.java) ?: false
            state.isGameOver = snapshot.child("gameOver").getValue(Boolean::class.java) ?: false
            state.winnerPlayerId = snapshot.child("winnerPlayerId").getValue(String::class.java)

            val playersMap = mutableMapOf<String, Player>()
            val playersSnapshot = snapshot.child("players")
            for (playerSnapshot in playersSnapshot.children) {
                val player = playerSnapshot.getValue(Player::class.java)
                if (player != null && playerSnapshot.key != null) {
                    playersMap[playerSnapshot.key!!] = player
                }
            }
            state.players = playersMap

            val pilesMap = mutableMapOf<String, Int>()
            val pilesSnapshot = snapshot.child("piles")
            for (pileSnapshot in pilesSnapshot.children) {
                val pileValue = pileSnapshot.getValue(Int::class.java)
                if (pileValue != null && pileSnapshot.key != null) {
                    pilesMap[pileSnapshot.key!!] = pileValue
                }
            }
            state.piles = pilesMap

            val deckList = mutableListOf<Int>()
            val deckSnapshot = snapshot.child("deck")
            for (cardSnapshot in deckSnapshot.children) {
                val card = cardSnapshot.getValue(Int::class.java)
                if (card != null) {
                    deckList.add(card)
                }
            }
            state.deck = deckList

            val turnOrderList = mutableListOf<String>()
            val turnOrderSnapshot = snapshot.child("playerTurnOrder")
            for (playerSnapshot in turnOrderSnapshot.children) {
                val playerId = playerSnapshot.getValue(String::class.java)
                if (playerId != null) {
                    turnOrderList.add(playerId)
                }
            }
            state.playerTurnOrder = turnOrderList

            return state
        } catch (e: Exception) {
            println("Error deserializing GameState: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun listenToGameChanges(gameId: String) {
        detachListener()
        val gameRef = database.child("games").child(gameId)
        currentGameRef = gameRef
        gameValueEventListener = gameRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newGameState = parseGameState(snapshot)
                if (newGameState != null) {
                    if (_gameState.value.currentPlayerId != newGameState.currentPlayerId && newGameState.currentPlayerId == currentUserId) {
                        cardsPlayedThisTurn = 0
                    }
                    _gameState.value = newGameState
                } else {
                    // If game data is null, it was deleted. Reset to lobby.
                    _gameState.value = GameState()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                println("Hiba a Firebase olvasásakor: ${error.message}")
                detachListener()
            }
        })
    }

    fun selectCard(card: Card) {
        _selectedCard.value = if (_selectedCard.value == card) null else card
    }

    fun playCardOnPile(pileIndex: String) {
        val cardToPlay = _selectedCard.value ?: return
        if (currentUserId != _gameState.value.currentPlayerId) {
            return
        }

        if (!GameLogic.isValidMove(cardToPlay, pileIndex, _gameState.value.piles)) {
            _selectedCard.value = null
            return
        }

        val currentGameState = _gameState.value
        val currentPlayer = currentGameState.players[currentUserId] ?: return

        val updatedHand = currentPlayer.hand.toMutableList().apply { remove(cardToPlay.number) }
        val updatedPlayer = currentPlayer.copy(hand = updatedHand)

        val updatedPlayers = currentGameState.players.toMutableMap().apply {
            this[currentUserId] = updatedPlayer
        }

        val updatedPiles = currentGameState.piles.toMutableMap().apply {
            this[pileIndex] = cardToPlay.number
        }

        var newGameState = currentGameState.copy(
            players = updatedPlayers,
            piles = updatedPiles,
            lastPlayedPileIndex = pileIndex
        )

        cardsPlayedThisTurn++
        newGameState = GameLogic.checkGameOver(newGameState, cardsPlayedThisTurn, isEndOfTurn = false)

        database.child("games").child(currentGameState.gameId).setValue(newGameState)
        _selectedCard.value = null
    }

    fun endTurn() {
        if (currentUserId != _gameState.value.currentPlayerId) {
            return
        }
        if (!canEndTurn()) {
            return
        }

        val currentGameState = _gameState.value
        val currentPlayer = currentGameState.players[currentUserId] ?: return

        val cardsToDrawCount = (GameLogic.calculateInitialHandSize(currentGameState.players.size) - currentPlayer.hand.size)
            .coerceAtLeast(0)

        val mutableDeck = currentGameState.deck.toMutableList()
        val drawnCards = mutableDeck.take(cardsToDrawCount)
        mutableDeck.removeAll(drawnCards)

        val updatedHand = (currentPlayer.hand + drawnCards).sorted().toMutableList()
        val updatedPlayer = currentPlayer.copy(hand = updatedHand)

        val updatedPlayers = currentGameState.players.toMutableMap().apply {
            this[currentUserId] = updatedPlayer
        }

        val turnOrder = currentGameState.playerTurnOrder
        val currentPlayerIndex = turnOrder.indexOf(currentUserId)
        val nextPlayerIndex = (currentPlayerIndex + 1) % turnOrder.size
        val nextPlayerId = turnOrder[nextPlayerIndex]

        var newGameState = currentGameState.copy(
            players = updatedPlayers,
            deck = mutableDeck,
            currentPlayerId = nextPlayerId,
            lastPlayedCardsCount = cardsPlayedThisTurn,
            lastPlayedPileIndex = null // Clear the highlight on turn end
        )

        newGameState = GameLogic.checkGameOver(newGameState, 0, isEndOfTurn = true)

        database.child("games").child(currentGameState.gameId).setValue(newGameState)
    }

    fun canEndTurn(): Boolean {
        if (currentUserId != _gameState.value.currentPlayerId) return false
        if (_gameState.value.deck.isEmpty()) {
            return cardsPlayedThisTurn >= GameLogic.MIN_CARDS_TO_PLAY_PER_TURN_EMPTY_DECK
        }
        return cardsPlayedThisTurn >= GameLogic.MIN_CARDS_TO_PLAY_PER_TURN
    }
}
