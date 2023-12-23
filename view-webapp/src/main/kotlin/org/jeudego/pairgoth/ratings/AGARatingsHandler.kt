package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import java.net.URL

object AGARatingsHandler: RatingsHandler(RatingsManager.Ratings.AGA) {
    override val defaultURL: URL by lazy {
        throw Error("No URL for AGA...")
    }
    override val active = false
    override fun parsePayload(payload: String): Json.Array {
        return Json.Array()
    }
}
