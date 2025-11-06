package com.example.thegame.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.thegame.data.Card
import com.example.thegame.data.GameState
import com.example.thegame.data.Player
import com.example.thegame.ui.theme.TheGameOnlineTheme
import com.example.thegame.viewmodel.GameViewModel

@Composable
fun TheGameScreen(viewModel: GameViewModel = viewModel()) {
    val gameState by viewModel.gameState.collectAsState()
    val isUserAuthenticated by viewModel.isUserAuthenticated.collectAsState()
    val selectedCard by viewModel.selectedCard
    val currentUserId = viewModel.currentUserId

    if (!isUserAuthenticated) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (!gameState.isGameStarted && gameState.gameId.isEmpty()) {
        LobbyScreen(
            onHostGame = { viewModel.createGame() },
            onJoinGame = { gameId -> viewModel.joinGame(gameId) },
            isAuthReady = isUserAuthenticated
        )
    } else if (!gameState.isGameStarted) {
        // Váróterem: Amíg a játék el nem indul
        WaitingRoomScreen(
            gameState = gameState,
            currentUserId = currentUserId,
            onStartGame = { viewModel.startGame() },
            onNameChange = { newName -> viewModel.setPlayerName(newName) }
        )
    } else {
        // Maga a játék
        GamePlayScreen(
            gameState = gameState,
            currentUserId = currentUserId,
            selectedCard = selectedCard,
            viewModel = viewModel
        )
    }
}

@Composable
fun LobbyScreen(onHostGame: () -> Unit, onJoinGame: (String) -> Unit, isAuthReady: Boolean) {
    var gameIdInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onHostGame, 
            enabled = isAuthReady, 
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Játék létrehozása")
        }
        Spacer(modifier = Modifier.height(32.dp))
        TextField(
            value = gameIdInput,
            onValueChange = { gameIdInput = it },
            label = { Text("Játék azonosító") },
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = { onJoinGame(gameIdInput) },
            enabled = gameIdInput.isNotBlank() && isAuthReady,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Csatlakozás játékhoz")
        }
    }
}

@Composable
fun WaitingRoomScreen(
    gameState: GameState,
    currentUserId: String,
    onStartGame: () -> Unit,
    onNameChange: (String) -> Unit
) {
    val isHost = gameState.players[currentUserId]?.isHost ?: false
    var newName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Játék ID: ${gameState.gameId}", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        // Névválasztó mező
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Válassz nevet") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onNameChange(newName) }, enabled = newName.isNotBlank()) {
                Text("Mentés")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Text("Játékosok:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        gameState.players.values.forEach { player ->
            Text("${player.name} ${if (player.isHost) "(Host)" else ""}")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isHost) {
            Button(onClick = onStartGame, enabled = gameState.players.size >= 2) {
                Text("Játék indítása")
            }
        } else {
            Text("Várj, amíg a host elindítja a játékot...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun GamePlayScreen(
    gameState: GameState,
    currentUserId: String,
    selectedCard: Card?,
    viewModel: GameViewModel
) {
    val currentPlayer = gameState.players[currentUserId]
    val isMyTurn = gameState.currentPlayerId == currentUserId

    if (gameState.isGameOver) {
        AlertDialog(
            onDismissRequest = { /* No dismiss, must take action */ },
            title = { Text("Játék vége!") },
            text = { Text(if (gameState.winnerPlayerId == "all") "Gratulálunk, megnyertétek!" else "Sajnos vesztettetek.") },
            confirmButton = {
                Button(onClick = { viewModel.returnToLobby() }) {
                    Text("Vissza a Lobbyba")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.resetGame() }) {
                    Text("Kilépés")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2E7D32)) // Sötétzöld háttér
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Soron van: ${gameState.players[gameState.currentPlayerId]?.name ?: "Ismeretlen"}",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Dobósorok (Piles)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emelkedő sorok
            PileView(topCard = gameState.piles["1"]!!, pileId = "1", lastPlayedPile = gameState.lastPlayedPileIndex, isAscending = true, isEnabled = isMyTurn && selectedCard != null) {
                viewModel.playCardOnPile("1")
            }
            PileView(topCard = gameState.piles["2"]!!, pileId = "2", lastPlayedPile = gameState.lastPlayedPileIndex, isAscending = true, isEnabled = isMyTurn && selectedCard != null) {
                viewModel.playCardOnPile("2")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ereszkedő sorok
            PileView(topCard = gameState.piles["3"]!!, pileId = "3", lastPlayedPile = gameState.lastPlayedPileIndex, isAscending = false, isEnabled = isMyTurn && selectedCard != null) {
                viewModel.playCardOnPile("3")
            }
            PileView(topCard = gameState.piles["4"]!!, pileId = "4", lastPlayedPile = gameState.lastPlayedPileIndex, isAscending = false, isEnabled = isMyTurn && selectedCard != null) {
                viewModel.playCardOnPile("4")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pakli mérete
        Text(
            text = "Pakli: ${gameState.deck.size} kártya",
            color = Color.White,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Játékos keze
        if (currentPlayer != null) {
            PlayerHandView(
                hand = currentPlayer.hand.map { Card(it) },
                selectedCard = selectedCard,
                onCardClick = { card -> viewModel.selectCard(card) },
                isMyTurn = isMyTurn
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Kör vége gomb
        Button(
            onClick = { viewModel.endTurn() },
            enabled = isMyTurn && viewModel.canEndTurn(),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Kör vége / Húzás")
        }
    }
}


@Composable
fun PileView(topCard: Int, pileId: String, lastPlayedPile: String?, isAscending: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    val isLastPlayed = pileId == lastPlayedPile
    val normalColor = if (isAscending) Color(0xFFADD8E6) else Color(0xFFFFCCCC)
    val animatedColor = remember { Animatable(0f) }

    LaunchedEffect(topCard, lastPlayedPile) {
        if (isLastPlayed) {
            // Flash for 2 seconds
            animatedColor.animateTo(
                targetValue = 1f,
                animationSpec = repeatable(
                    iterations = 4,
                    animation = tween(durationMillis = 250, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            animatedColor.snapTo(0f) // Return to normal after flashing
        } else {
            animatedColor.snapTo(0f)
        }
    }

    val color = lerp(normalColor, Color.Yellow, animatedColor.value)

    Card(
        modifier = Modifier
            .size(100.dp, 150.dp)
            .padding(4.dp)
            .clickable(enabled = isEnabled, onClick = onClick)
            .border(
                BorderStroke(
                    width = 2.dp, 
                    brush = SolidColor(if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray)
                ),
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = color
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isAscending) "↑" else "↓",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = topCard.toString(),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black
            )
        }
    }
}

@Composable
fun PlayerHandView(hand: List<Card>, selectedCard: Card?, onCardClick: (Card) -> Unit, isMyTurn: Boolean) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color(0xFF424242), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(hand) { card ->
            val isSelected = card == selectedCard
            Card(
                modifier = Modifier
                    .size(80.dp, 120.dp)
                    .padding(4.dp)
                    .clickable(enabled = isMyTurn) { onCardClick(card) }
                    .border(
                        width = if (isSelected) 4.dp else 0.dp,
                        color = if (isSelected) Color.Yellow else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = card.number.toString(), color = if (isMyTurn) Color.Black else Color.Gray, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Preview a fejlesztéshez
@Preview(showBackground = true)
@Composable
fun PreviewTheGameScreen() {
    TheGameOnlineTheme {
        // Hozz létre egy mock GameState-et a preview-hoz
        val mockPlayer1 = Player("player1", "Alice", isHost = true)
        val mockPlayer2 = Player("player2", "Bob")
        val mockPlayers = mutableMapOf(mockPlayer1.id to mockPlayer1, mockPlayer2.id to mockPlayer2)

        val mockGameState = GameState(
            gameId = "test1234",
            players = mockPlayers,
            piles = mutableMapOf("1" to 5, "2" to 12, "3" to 95, "4" to 88),
            deck = (1..50).toMutableList(),
            currentPlayerId = "player1",
            isGameStarted = true
        )
        // A ViewModel-t itt nem tudjuk teljesen mockolni, de a képernyő komponensek preview-ja lehetséges
        // GamePlayScreen(gameState = mockGameState, currentUserId = "player1", viewModel = GameViewModel())
        LobbyScreen(onHostGame = {}, onJoinGame = {}, isAuthReady = true)
    }
}
