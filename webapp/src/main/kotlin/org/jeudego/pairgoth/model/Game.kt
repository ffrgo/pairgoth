package org.jeudego.pairgoth.model

import org.jeudego.pairgoth.store.Store

data class Game(val white: Int, val black: Int, var result: Char = '?', val id: Int = Store.nextGameId)
