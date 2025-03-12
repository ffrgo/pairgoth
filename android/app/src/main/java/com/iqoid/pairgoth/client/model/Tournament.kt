package com.iqoid.pairgoth.client.model

import com.google.gson.annotations.SerializedName

data class Tournament(
    @SerializedName("name") val name: String? = null,
    @SerializedName("lastModified") val lastModified: String? = null
)
