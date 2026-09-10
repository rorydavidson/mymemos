package com.keltruc.mymemos.data.repository

/**
 * Today, already written out. A template says {{weekday}}; only the platform knows whether
 * that is "Thursday" or "Iau", so the words are supplied rather than formatted here.
 */
interface TemplateValues {
    val date: String
    val isoDate: String
    val time: String
    val weekday: String
    val month: String
    val year: String
}
