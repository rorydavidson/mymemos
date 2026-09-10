package com.keltruc.mymemos.model

data class ServerProfile(
    val version: String,
    val instanceUrl: String,
    val demo: Boolean,
    val needsSetup: Boolean,
)
