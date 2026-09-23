package com.terasumi.sellerkeyboard.seller

enum class SellerTopic {
    CHECKOUT_PAYMENT, EDIT_CANCEL_ORDER, ORDER_STATUS_TRACKING, PRICE_DISCOUNT,
    PRODUCT_DETAILS_AVAILABILITY, RETURNS_WARRANTY, SHIPPING, STORE_INFO,
    WHOLESALE, GREETING_FOLLOW_UP
}

data class SellerTopicMatch(val topic: SellerTopic, val confidence: Double)

object SellerTopicClassifier {
    private val keywords = mapOf(
        SellerTopic.CHECKOUT_PAYMENT to listOf("payment", "thanh toán", "cod", "checkout"),
        SellerTopic.EDIT_CANCEL_ORDER to listOf("cancel", "hủy đơn", "sửa đơn", "edit order"),
        SellerTopic.ORDER_STATUS_TRACKING to listOf("tracking", "mã vận đơn", "đơn ở đâu", "order status"),
        SellerTopic.PRICE_DISCOUNT to listOf("price", "giá", "discount", "giảm giá"),
        SellerTopic.PRODUCT_DETAILS_AVAILABILITY to listOf("còn hàng", "available", "size", "màu"),
        SellerTopic.RETURNS_WARRANTY to listOf("return", "đổi trả", "warranty", "bảo hành"),
        SellerTopic.SHIPPING to listOf("shipping", "giao hàng", "phí ship", "delivery"),
        SellerTopic.STORE_INFO to listOf("địa chỉ shop", "store", "giờ mở cửa"),
        SellerTopic.WHOLESALE to listOf("wholesale", "sỉ", "số lượng lớn"),
        SellerTopic.GREETING_FOLLOW_UP to listOf("xin chào", "hello", "cảm ơn", "follow up")
    )

    fun classifyExplicitText(text: String): SellerTopicMatch? {
        val normalized = SellerSearchNormalizer.normalize(text)
        val scores = keywords.mapValues { (_, terms) ->
            terms.count { SellerSearchNormalizer.normalize(it) in normalized }
        }
        val best = scores.maxByOrNull { it.value }?.takeIf { it.value > 0 } ?: return null
        return SellerTopicMatch(best.key, (0.55 + best.value * 0.15).coerceAtMost(0.95))
    }
}
