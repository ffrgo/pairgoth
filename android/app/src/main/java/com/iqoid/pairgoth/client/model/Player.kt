package com.iqoid.pairgoth.client.model

data class Player(
    val id: String? = null, // Nullable because it's assigned by the server on registration
    val name: String,
    val firstname: String,
    val country: String,
    val club: String,
    val rank: String,
    val rating: Int,
    val skip: List<Int> = emptyList(), //optional property
    val final: Boolean = false, //optional property
    val egf:String?=null, //optional property
    val ffg:String?=null //optional property
    // ... other relevant properties ...
)
