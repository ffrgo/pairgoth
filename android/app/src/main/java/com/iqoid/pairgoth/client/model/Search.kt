package com.iqoid.pairgoth.client.model

data class Search(
    val needle:String,
    val egf:Boolean,
    val ffg:Boolean,
    val countryFilter:String? = null
    // ... other relevant properties ...
)
