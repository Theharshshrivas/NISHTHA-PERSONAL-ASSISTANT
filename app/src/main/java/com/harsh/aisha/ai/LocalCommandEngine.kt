
package com.harsh.aisha.ai

import java.text.DateFormat
import java.util.Date

enum class LocalIntent {
    GREETING,
    ASK_NAME,
    ASK_TIME,
    ASK_DATE,
    HELP,
    UNKNOWN
}

data class LocalReply(
    val intent: LocalIntent,
    val text: String,
    val handled: Boolean
)

/**
 * Lightweight offline command interpreter.
 *
 * This is NOT an LLM. It handles a small set of deterministic intents
 * while the optional local model runtime is unavailable.
 */
class LocalCommandEngine(
    private val assistantName: String = "NISHTHA"
) {

    fun process(rawInput: String): LocalReply {
        val input = rawInput.trim().lowercase()

        if (input.isBlank()) {
            return LocalReply(
                LocalIntent.UNKNOWN,
                "Kuch likho ya bolo, phir main help kar paungi.",
                true
            )
        }

        return when {
            containsAny(input, "hello", "hi", "namaste", "नमस्ते", "हेलो") ->
                LocalReply(
                    LocalIntent.GREETING,
                    "Namaste! Main $assistantName hoon. Batao, kya help chahiye?",
                    true
                )

            containsAny(
                input,
                "tumhara naam",
                "aapka naam",
                "your name",
                "who are you",
                "तुम्हारा नाम"
            ) ->
                LocalReply(
                    LocalIntent.ASK_NAME,
                    "Mera naam $assistantName hai.",
                    true
                )

            containsAny(
                input,
                "time kya",
                "current time",
                "what time",
                "समय क्या",
                "kitne baje"
            ) ->
                LocalReply(
                    LocalIntent.ASK_TIME,
                    "Abhi " + DateFormat.getTimeInstance(
                        DateFormat.SHORT
                    ).format(Date()),
                    true
                )

            containsAny(
                input,
                "aaj ki date",
                "today date",
                "what date",
                "आज की तारीख",
                "date today"
            ) ->
                LocalReply(
                    LocalIntent.ASK_DATE,
                    "Aaj " + DateFormat.getDateInstance(
                        DateFormat.FULL
                    ).format(Date()),
                    true
                )

            containsAny(input, "help", "madad", "kya kar sakti", "मदद") ->
                LocalReply(
                    LocalIntent.HELP,
                    "Main offline basic commands handle kar sakti hoon. Full local AI model abhi install aur connect nahi hua hai.",
                    true
                )

            else ->
                LocalReply(
                    LocalIntent.UNKNOWN,
                    "Is request ke liye abhi local AI model available nahi hai. Internet ya external AI ko bina tumhari approval ke use nahi kiya jayega.",
                    false
                )
        }
    }

    private fun containsAny(input: String, vararg phrases: String): Boolean {
        return phrases.any { input.contains(it) }
    }
}
