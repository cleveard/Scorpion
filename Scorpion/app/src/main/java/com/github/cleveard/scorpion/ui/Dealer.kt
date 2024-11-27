package com.github.cleveard.scorpion.ui

import com.github.cleveard.scorpion.db.CardEntity

interface Dealer {
    val deck: List<CardEntity>
    fun shuffle(): List<CardEntity>
    fun findCard(cardValue: Int): CardEntity?
}

