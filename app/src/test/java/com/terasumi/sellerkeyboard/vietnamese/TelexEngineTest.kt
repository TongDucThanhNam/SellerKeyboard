package com.terasumi.sellerkeyboard.vietnamese

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelexEngineTest {
    private val engine = TelexEngine()

    @Test
    fun transform_passesFullTelexMatrix() {
        assertTrue("Expected at least 100 Telex matrix cases", telexCases.size >= 100)

        telexCases.forEach { (raw, expected) ->
            assertEquals("raw=$raw", expected, engine.transform(raw))
        }
    }

    @Test
    fun transform_keepsTonePlacementOnCommonVietnameseWords() {
        commonWordCases.forEach { (raw, expected) ->
            assertEquals("raw=$raw", expected, engine.transform(raw))
        }
    }

    private companion object {
        private val shapeCases = listOf(
            "aa" to "â",
            "aw" to "ă",
            "ee" to "ê",
            "oo" to "ô",
            "ow" to "ơ",
            "uw" to "ư",
            "dd" to "đ",
            "AA" to "Â",
            "AW" to "Ă",
            "EE" to "Ê",
            "OO" to "Ô",
            "OW" to "Ơ",
            "UW" to "Ư",
            "DD" to "Đ"
        )

        private val plainToneCases = listOf(
            toneCases("a", "á", "à", "ả", "ã", "ạ"),
            toneCases("e", "é", "è", "ẻ", "ẽ", "ẹ"),
            toneCases("i", "í", "ì", "ỉ", "ĩ", "ị"),
            toneCases("o", "ó", "ò", "ỏ", "õ", "ọ"),
            toneCases("u", "ú", "ù", "ủ", "ũ", "ụ"),
            toneCases("y", "ý", "ỳ", "ỷ", "ỹ", "ỵ"),
            toneCases("A", "Á", "À", "Ả", "Ã", "Ạ"),
            toneCases("E", "É", "È", "Ẻ", "Ẽ", "Ẹ"),
            toneCases("I", "Í", "Ì", "Ỉ", "Ĩ", "Ị"),
            toneCases("O", "Ó", "Ò", "Ỏ", "Õ", "Ọ"),
            toneCases("U", "Ú", "Ù", "Ủ", "Ũ", "Ụ"),
            toneCases("Y", "Ý", "Ỳ", "Ỷ", "Ỹ", "Ỵ")
        ).flatten()

        private val shapedToneCases = listOf(
            toneCases("aa", "ấ", "ầ", "ẩ", "ẫ", "ậ"),
            toneCases("aw", "ắ", "ằ", "ẳ", "ẵ", "ặ"),
            toneCases("ee", "ế", "ề", "ể", "ễ", "ệ"),
            toneCases("oo", "ố", "ồ", "ổ", "ỗ", "ộ"),
            toneCases("ow", "ớ", "ờ", "ở", "ỡ", "ợ"),
            toneCases("uw", "ứ", "ừ", "ử", "ữ", "ự"),
            toneCases("AA", "Ấ", "Ầ", "Ẩ", "Ẫ", "Ậ"),
            toneCases("AW", "Ắ", "Ằ", "Ẳ", "Ẵ", "Ặ"),
            toneCases("EE", "Ế", "Ề", "Ể", "Ễ", "Ệ"),
            toneCases("OO", "Ố", "Ồ", "Ổ", "Ỗ", "Ộ"),
            toneCases("OW", "Ớ", "Ờ", "Ở", "Ỡ", "Ợ"),
            toneCases("UW", "Ứ", "Ừ", "Ử", "Ữ", "Ự")
        ).flatten()

        private val commandCases = listOf(
            "asf" to "à",
            "afj" to "ạ",
            "asx" to "ã",
            "aaf" to "ầ",
            "aas" to "ấ",
            "awr" to "ẳ",
            "ees" to "ế",
            "oos" to "ố",
            "ows" to "ớ",
            "uws" to "ứ",
            "asz" to "a",
            "aafz" to "â",
            "zalo" to "zalo",
            "az" to "az",
            "herr" to "her",
            "ass" to "as",
            "aff" to "af",
            "arr" to "ar",
            "axx" to "ax",
            "ajj" to "aj",
            "cuar" to "của",
            "CUAR" to "CỦA",
            "quas" to "quá"
        )

        private val commonWordCases = listOf(
            "ddawng" to "đăng",
            "nhaan" to "nhân",
            "tieesng" to "tiếng",
            "muoons" to "muốn",
            "thuwowng" to "thương",
            "khachs" to "khách",
            "hangf" to "hàng",
            "quas" to "quá",
            "gias" to "giá",
            "dduwowngf" to "đường",
            "nghieeng" to "nghiêng",
            "chaoj" to "chạo",
            "toosi" to "tối",
            "khuyeens" to "khuyến"
        )

        private val telexCases = shapeCases + plainToneCases + shapedToneCases + commandCases + commonWordCases

        private fun toneCases(
            rawBase: String,
            acute: String,
            grave: String,
            hook: String,
            tilde: String,
            dot: String
        ): List<Pair<String, String>> {
            return listOf(
                "${rawBase}s" to acute,
                "${rawBase}f" to grave,
                "${rawBase}r" to hook,
                "${rawBase}x" to tilde,
                "${rawBase}j" to dot
            )
        }
    }
}
