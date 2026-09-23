package com.terasumi.sellerkeyboard.suggestion

internal object SellerLexicon {
    val words: Map<String, Int> = listOf(
        "ạ", "bạn", "bảo hành", "cảm ơn", "chào", "chuyển khoản", "còn hàng", "đặt hàng",
        "địa chỉ", "đơn hàng", "đổi trả", "giá", "giảm giá", "giao hàng", "hàng", "hỗ trợ",
        "khách hàng", "khuyến mãi", "miễn phí", "nhận hàng", "phí", "sản phẩm", "shop",
        "thanh toán", "theo dõi", "vận chuyển", "vui lòng", "xin chào", "xác nhận",
        "available", "cancel", "cash", "checkout", "customer", "delivery", "discount", "hello",
        "order", "payment", "price", "product", "refund", "return", "seller", "shipping",
        "stock", "thanks", "tracking", "warranty"
    ).flatMap { phrase -> phrase.split(' ') }
        .distinct()
        .mapIndexed { index, word -> word to (10_000 - index * 31) }
        .toMap()

    val nextWords: Map<String, List<String>> = mapOf(
        "xin" to listOf("chào"),
        "cảm" to listOf("ơn"),
        "đơn" to listOf("hàng"),
        "giao" to listOf("hàng"),
        "sản" to listOf("phẩm"),
        "giảm" to listOf("giá"),
        "vui" to listOf("lòng"),
        "thank" to listOf("you"),
        "order" to listOf("status"),
        "free" to listOf("shipping")
    )
}
