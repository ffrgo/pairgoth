package org.jeudego.pairgoth.ratings

import com.republicate.kson.Json
import java.net.URL
import java.time.LocalDate

object AGARatingsHandler: RatingsHandler(RatingsManager.Ratings.AGA) {
    override val defaultURL: URL by lazy {
        throw Error("No functional URL for AGA...")
    }
    override val active = false
    override fun parsePayload(payload: String): Pair<LocalDate, Json.Array> {
        return Pair(
            LocalDate.MIN,
            Json.Array()
        )
    }
}
