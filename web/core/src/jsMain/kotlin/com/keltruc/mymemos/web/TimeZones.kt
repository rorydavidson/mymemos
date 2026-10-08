package com.keltruc.mymemos.web

@JsModule("@js-joda/timezone")
@JsNonModule
external object JsJodaTimeZoneModule

/**
 * Referenced at start-up so the bundler keeps the zone database. Eager because the shared
 * tests reach time zones without passing through anything web-specific.
 */
@OptIn(ExperimentalStdlibApi::class)
@EagerInitialization
private val timeZones = JsJodaTimeZoneModule
