package com.iqoid.pairgoth.client.model

import com.google.gson.annotations.SerializedName

data class Standing(
    val id: Int? = null,
    val name: String? = null,
    val firstname: String? = null,
    val rating: Int? = null,
    val rank: Int? = null,
    val country: String? = null,
    val club: String? = null,
    val final: Boolean? = null,
    @SerializedName("mmsCorrection") val mmsCorrection: Int? = null,
    val egf: String? = null,
    @SerializedName("MMS") val mms: Int? = null,
    @SerializedName("SOSM") val sosm: Int? = null,
    @SerializedName("SOSOSM") val sososm: Int? = null,
    @SerializedName("NBW") val nbw: Int? = null,
    @SerializedName("RATING") val rating2: Int? = null,
    val results: List<String>? = null,
    val num: Int? = null,
    val place: Int? = null
)
