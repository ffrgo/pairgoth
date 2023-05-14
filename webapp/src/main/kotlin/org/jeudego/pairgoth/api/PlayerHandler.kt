package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

object PlayerHandler: ApiHandler {

    override fun post(request: HttpServletRequest): Json {
        val payload = getObjectPayload(request)

        // player parsing
        val player = Player.fromJson(payload)

        Store.addPlayer(player)
        return Json.Object("success" to true, "id" to player.id)
    }

    override fun get(request: HttpServletRequest): Json {
        return when (val id = getSelector(request)?.toIntOrNull()) {
            null -> Json.Array(Store.getPlayersIDs())
            else -> Store.getPlayer(id)?.toJson() ?: ApiHandler.badRequest("no player with id #${id}")
        }
    }

    override fun put(request: HttpServletRequest): Json {
        val id = getSelector(request)?.toIntOrNull() ?: ApiHandler.badRequest("missing or invalid player selector")
        TODO()
    }

}
