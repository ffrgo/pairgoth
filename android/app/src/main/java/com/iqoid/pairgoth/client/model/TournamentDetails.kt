package com.iqoid.pairgoth.client.model

import com.google.gson.annotations.SerializedName

data class TournamentDetails(
    val id: Int,
    val type: String,
    val name: String,
    val shortName: String,
    val startDate: String,
    val endDate: String,
    val director: String,
    val country: String,
    val location: String,
    val online: Boolean,
    val komi: Double,
    val rules: String,
    val gobanSize: Int,
    @SerializedName("timeSystem")
    val timeSystem: TimeSystemDetails,
    val rounds: Int,
    @SerializedName("pairing")
    val pairing: PairingDetails,
    //Removed stats, teamSize and frozen because they are not used by the fragment
)

data class TimeSystemDetails(
    val type: String,
    val mainTime: Int,
    val increment: Int?
)

data class PairingDetails(
    val type: String,
    val base: BasePairingDetails,
    val main: MainPairingDetails,
    val secondary: SecondaryPairingDetails,
    val geo: GeoPairingDetails,
    val handicap: HandicapPairingDetails,
    val placement: List<String>,
    val mmFloor: Int,
    val mmBar: Int
)

data class BasePairingDetails(
    val nx1: Double,
    val dupWeight: Double,
    val random: Double,
    val deterministic: Boolean,
    val colorBalanceWeight: Double
)
data class MainPairingDetails(
    @SerializedName("catWeight")
    val categoriesWeight: Double,
    val scoreWeight: Double,
    val upDownWeight: Double,
    val upDownCompensate: Boolean,
    val upDownLowerMode: String,
    val upDownUpperMode: String,
    val maximizeSeeding: Double,
    val firstSeedLastRound: Int,
    val firstSeed: String,
    val secondSeed: String,
    val firstSeedAddCrit: String,
    val secondSeedAddCrit: String,
    val mmsValueAbsent: Double,
    val roundDownScore: Boolean,
    val sosValueAbsentUseBase: Boolean
)

data class SecondaryPairingDetails(
    val barThreshold: Boolean,
    val rankThreshold: Int,
    val winsThreshold: Boolean,
    val secWeight: Double
)

data class GeoPairingDetails(
    val weight: Double,
    val mmsDiffCountry: Int,
    val mmsDiffClubGroup: Int,
    val mmsDiffClub: Int
)

data class HandicapPairingDetails(
    val weight: Double,
    val useMMS: Boolean,
    val threshold: Int,
    val correction: Int,
    val ceiling: Int
)
