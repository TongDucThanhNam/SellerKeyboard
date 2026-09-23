package com.terasumi.sellerkeyboard.seller

import com.terasumi.sellerkeyboard.clipboard.ClipboardEntityType
import com.terasumi.sellerkeyboard.clipboard.ParsedClipboardEntity

object TemplateVariableMapper {
    fun suggestedVariable(entity: ParsedClipboardEntity): String? = when (entity.type) {
        ClipboardEntityType.URL, ClipboardEntityType.PRODUCT_LINK -> "product_url"
        ClipboardEntityType.PRICE -> "price"
        ClipboardEntityType.ORDER_ID -> "order_id"
        ClipboardEntityType.TRACKING_CODE -> "tracking_code"
        ClipboardEntityType.DISCOUNT_CODE -> "discount_code"
        else -> null
    }
}
