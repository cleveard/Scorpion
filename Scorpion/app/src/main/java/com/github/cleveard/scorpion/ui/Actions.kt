package com.github.cleveard.scorpion.ui

import com.github.cleveard.scorpion.db.Card

interface Actions {
    fun onClick(card: Card)
    fun onDoubleClick(card: Card)
}
