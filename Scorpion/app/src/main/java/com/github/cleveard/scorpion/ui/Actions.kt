package com.github.cleveard.scorpion.ui

import com.github.cleveard.scorpion.db.CardEntity

interface Actions {
    fun isClickable(card: CardEntity): Boolean
    fun onClick(card: CardEntity)
    fun onDoubleClick(card: CardEntity)
}
