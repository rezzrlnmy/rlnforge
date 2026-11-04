package com.rln.forge.providers

import org.json.JSONObject

/**
 * Pure SSE payload parsers — no Android, no network, unit-tested.
 * Each function takes the JSON text after the `data:` prefix of one SSE line.
 */

/** OpenAI-compatible: `choices[0].delta.content`. "[DONE]" yields null. */
fun openAiDelta(data: String): String? {
    val trimmed = data.trim()
    if (trimmed.isEmpty() || trimmed == "[DONE]") return null
    val content = runCatching {
        JSONObject(trimmed)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content")
    }.getOrNull()
    return content?.takeIf { it.isNotEmpty() }
}

/** Anthropic: `content_block_delta` with `delta.type == "text_delta"`. */
fun anthropicDelta(data: String): String? {
    val trimmed = data.trim()
    if (trimmed.isEmpty()) return null
    return runCatching {
        val json = JSONObject(trimmed)
        if (json.optString("type") != "content_block_delta") return@runCatching null
        val delta = json.optJSONObject("delta") ?: return@runCatching null
        if (delta.optString("type") != "text_delta") return@runCatching null
        delta.optString("text").takeIf { it.isNotEmpty() }
    }.getOrNull()
}

/** Gemini: `candidates[0].content.parts[].text` joined. */
fun geminiTexts(data: String): String {
    val trimmed = data.trim()
    if (trimmed.isEmpty()) return ""
    return runCatching {
        val parts = JSONObject(trimmed)
            .optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: return@runCatching ""
        val out = StringBuilder()
        for (i in 0 until parts.length()) {
            out.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }
        out.toString()
    }.getOrDefault("")
}

/** Best-effort error text from a provider error body. Never includes keys. */
fun errorMessage(body: String, fallback: String): String {
    val trimmed = body.trim()
    if (trimmed.isEmpty()) return fallback
    val parsed = runCatching {
        val json = JSONObject(trimmed)
        val error = json.opt("error")
        when (error) {
            is JSONObject -> error.optString("message").takeIf { it.isNotEmpty() }
            is String -> error.takeIf { it.isNotEmpty() }
            else -> json.optString("message").takeIf { it.isNotEmpty() }
        }
    }.getOrNull()
    return parsed ?: trimmed.take(300)
}

/** Number of models in a `/v1/models`-style response, if parseable. */
fun countModels(body: String): Int? {
    return runCatching {
        JSONObject(body).optJSONArray("data")?.length()
            ?: JSONObject(body).optJSONArray("models")?.length()
    }.getOrNull()
}
