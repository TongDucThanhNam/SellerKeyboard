package com.terasumi.sellerkeyboard.seller

import com.terasumi.sellerkeyboard.clipboard.ClipboardEntityType
import com.terasumi.sellerkeyboard.clipboard.ParsedClipboardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemplateVariableMapperTest {
    @Test
    fun commercialFragments_mapOnlyToExplicitCompatibleVariables() {
        fun entity(type: ClipboardEntityType) = ParsedClipboardEntity(type, "x", 0, 1)
        assertEquals("order_id", TemplateVariableMapper.suggestedVariable(entity(ClipboardEntityType.ORDER_ID)))
        assertEquals("tracking_code", TemplateVariableMapper.suggestedVariable(entity(ClipboardEntityType.TRACKING_CODE)))
        assertEquals("price", TemplateVariableMapper.suggestedVariable(entity(ClipboardEntityType.PRICE)))
        assertEquals("product_url", TemplateVariableMapper.suggestedVariable(entity(ClipboardEntityType.PRODUCT_LINK)))
        assertNull(TemplateVariableMapper.suggestedVariable(entity(ClipboardEntityType.PHONE)))
    }
}
