package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import org.jeudego.pairgoth.model.Player
import org.jeudego.pairgoth.model.Tournament
import org.jeudego.pairgoth.model.fromJson
import org.jeudego.pairgoth.store.Store
import javax.servlet.http.HttpServletRequest

class PlayerHandler: ApiHandler {

    override fun post(request: HttpServletRequest): Json {
        val json = getPayload(request)
        if (!json.isObject) ApiHandler.badRequest("expecting a json object")
        val payload = json.asObject()

        // player parsing
        val player = Player.fromJson(payload)

        Store.addPlayer(player)
        return Json.Object("success" to true, "id" to player.id)
    }
}