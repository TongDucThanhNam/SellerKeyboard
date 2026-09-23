package com.terasumi.sellerkeyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenContextParserTest {
    @Test
    fun parser_extractsBoundedCurrentAndPreviousTokens() {
        assertEquals(
            TokenContext("shi", "free"),
            TokenContextParser.parse("We offer free shi")
        )
        assertEquals(
            TokenContext("ORD-123", "mã"),
            TokenContextParser.parse("mã ORD-123")
        )
    }

    @Test
    fun trailingSpace_requestsNextWordForPreviousToken() {
        assertEquals(
            TokenContext("", "cảm"),
            TokenContextParser.parse("cảm ")
        )
    }
}
