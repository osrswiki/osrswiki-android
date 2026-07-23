package com.omiyawaki.osrswiki.network.model

import org.junit.Assert.assertNotNull
import org.junit.Test

class ArticleParseApiResponseTest {
    @Test
    fun articleParseApiResponse_allowsMissingParsePayload() {
        val response = ArticleParseApiResponse(parse = null)

        assertNotNull(response)
    }
}
