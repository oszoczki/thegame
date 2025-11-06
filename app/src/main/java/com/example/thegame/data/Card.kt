package com.example.thegame.data

data class Card(val number: Int) {
    // Firebase nem szereti az egyedi objektumokat Map-ben, egyszerűsítsük számmá
    fun toInt() = number
}