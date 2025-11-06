package com.example.thegame.game

import com.example.thegame.data.Card

class Deck {
    private val cards: MutableList<Card> = mutableListOf()

    init {
        for (i in 2..99) { // A The Game-ben nincs 1-es és 100-as kártya a pakliban
            cards.add(Card(i))
        }
        cards.shuffle()
    }

    fun draw(count: Int = 1): List<Card> {
        val drawn = mutableListOf<Card>()
        repeat(count) {
            if (cards.isNotEmpty()) {
                drawn.add(cards.removeAt(0))
            }
        }
        return drawn
    }

    fun remainingCards() = cards.size
}