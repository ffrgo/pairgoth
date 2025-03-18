package com.iqoid.pairgoth.client.model

data class Game(
    val id: Int, // game id
    val t: Int, // table
    val w: Int, // white player
    val b: Int, // black player
    val h: Int, // handicap
    val r: String // result
)
