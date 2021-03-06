package com.soywiz.korio.i18n

import com.soywiz.korio.*
import com.soywiz.korio.lang.*

// https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes
enum class Language(val iso6391: String, val iso6392: String) {
	JAPANESE("ja", "jpn"),
	ENGLISH("en", "eng"),
	FRENCH("fr", "fra"),
	SPANISH("es", "spa"),
	GERMAN("de", "deu"),
	ITALIAN("it", "ita"),
	DUTCH("nl", "nld"),
	PORTUGUESE("pt", "por"),
	RUSSIAN("ru", "rus"),
	KOREAN("ko", "kor"),
	CHINESE("zh", "zho"),
	;

	companion object {
		val BY_ID by lazy {
			(
					(values().map { it.name.toLowerCase() to it } +
							values().map { it.iso6391 to it } +
							values().map { it.iso6392 to it })
					).toMap()
		}

		operator fun get(id: String): Language? = BY_ID[id]
		//operator fun invoke(id: String): Language? = BY_ID[id]

		val SYSTEM_LANGS by lazy {
			KorioNative.systemLanguageStrings.map {
				// @TODO: kotlin-js bug ?. :: TypeError: item.split(...).firstOrNull is not a function
				// @TODO: Kotlin seems to be calling native's JS String.split wrongly and returning an Array instead of a List, this `KorioNative.systemLanguageStrings = comes from window.navigator.languages.toList()`
				//val parts = it?.split("-")
				//val part = parts?.firstOrNull()

				val part = it.substringBefore('-')

				BY_ID[part]
			}.filterNotNull()
		}
		val SYSTEM by lazy { SYSTEM_LANGS.firstOrNull() ?: ENGLISH }
		var CURRENT by lazyVar { SYSTEM }
	}
}
