package org.jeudego.pairgoth.api

import com.republicate.kson.Json
import com.republicate.kson.toJsonArray
import com.republicate.kson.toJsonObject
import org.jeudego.pairgoth.api.ApiHandler.Companion.badRequest
import org.jeudego.pairgoth.model.toJson
import org.jeudego.pairgoth.pairing.solver.CollectingListener
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object ExplainHandler: PairgothApiHandler {

    override fun get(request: HttpServletRequest, response: HttpServletResponse): Json? {
        val tournament = getTournament(request)
        val round = getSubSelector(request)?.toIntOrNull() ?: badRequest("invalid round number")
        if (round > tournament.lastRound() + 1) badRequest("invalid round: previous round has not been played")
        val paired = tournament.games(round).values.flatMap {
            listOf(it.black, it.white)
        }.filter {
            it != 0
        }.map {
            tournament.pairables[it] ?: throw Error("Unknown pairable ID: $it")
        }
        val games = tournament.games(round).map { it.value }.toList()
        // build the scores map by redoing the whole pairing
        tournament.unpair(round)
        val history = tournament.historyHelper(round)
        val weightsCollector = CollectingListener()
        tournament.pair(round, paired, false, weightsCollector)
        val weights = weightsCollector.out
        // Since weights are generally in two groups towards the min and the max,
        // compute the max of the low group ("low") and the min of the high group ("high")
        // to improve coloring.
        // Total weights axis:
        // ----[min]xxxx[low]----[middle]----[high]xxxx[max]---->
        val min = weights.values.minOfOrNull { it.values.sum() } ?: 0.0
        val max = weights.values.maxOfOrNull { it.values.sum() } ?: 0.0
        val middle = (max - min) / 2.0
        val low = weights.values.map { it.values.sum() }.filter { it < middle }.maxOrNull() ?: middle
        val high = weights.values.map { it.values.sum() }.filter { it > middle }.minOrNull() ?: middle
        val ret = Json.Object(
            "paired" to paired.sortedByDescending { 1000 * (history.scores[it.id] ?: 0.0) + (history.sos[it.id] ?: 0.0) }.map {
                it.toMutableJson().apply {
                    put("score", history.scores[it.id])
                    put("wins", history.wins[it.id])
                    put("sos", history.sos[it.id])
                    put("dudd", history.drawnUpDown[it.id])
                }
            }.toJsonArray(),
            // "games" to games.map { it.toJson() }.toJsonArray(),
            "games" to games.associateBy { "${it.white}-${it.black}" }.mapValues { it.value.toJson() }.toJsonObject(),
            "weights" to weights.entries.map { (key, value) ->
                Pair(
                    "${key.first}-${key.second}",
                    value.also {
                        it.put("total", it.values.sum())
                    }
                )
            }.toJsonObject(),
            "min" to min,
            "low" to low,
            "high" to high,
            "max" to max
        )
        return ret
    }
}
