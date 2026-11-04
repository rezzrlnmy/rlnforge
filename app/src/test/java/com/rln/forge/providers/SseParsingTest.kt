package com.rln.forge.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SseParsingTest {

    @Test
    fun openAiDelta_extractsContent() {
        val data = """{"choices":[{"delta":{"content":"Hello"}}]}"""
        assertEquals("Hello", openAiDelta(data))
    }

    @Test
    fun openAiDelta_doneAndEmptyYieldNull() {
        assertNull(openAiDelta("[DONE]"))
        assertNull(openAiDelta(""))
        assertNull(openAiDelta("""{"choices":[{"delta":{}}]}"""))
        assertNull(openAiDelta("not json"))
    }

    @Test
    fun anthropicDelta_extractsTextDeltaOnly() {
        val data = """{"type":"content_block_delta","delta":{"type":"text_delta","text":"Hi"}}"""
        assertEquals("Hi", anthropicDelta(data))
        assertNull(
            anthropicDelta(
                """{"type":"message_start","message":{"id":"m1"}}"""
            )
        )
        assertNull(
            anthropicDelta(
                """{"type":"content_block_delta","delta":{"type":"input_json_delta"}}"""
            )
        )
    }

    @Test
    fun geminiTexts_joinsParts() {
        val data = """{"candidates":[{"content":{"parts":[{"text":"Hello "},{"text":"world"}]}}]}"""
        assertEquals("Hello world", geminiTexts(data))
        assertEquals("", geminiTexts(""))
        assertEquals("", geminiTexts("""{"candidates":[]}"""))
        assertEquals("", geminiTexts("not json"))
    }

    @Test
    fun errorMessage_prefersProviderMessage() {
        assertEquals(
            "Invalid key",
            errorMessage("""{"error":{"message":"Invalid key","type":"auth"}}""", "fallback")
        )
        assertEquals(
            "Bad request",
            errorMessage("""{"error":"Bad request"}""", "fallback")
        )
        assertEquals("fallback", errorMessage("", "fallback"))
        assertEquals("plain text error", errorMessage("plain text error", "fallback"))
    }

    @Test
    fun countModels_countsDataArray() {
        assertEquals(
            2,
            countModels("""{"data":[{"id":"a"},{"id":"b"}]}""")
        )
        assertNull(countModels("""{"object":"list"}"""))
    }
}
