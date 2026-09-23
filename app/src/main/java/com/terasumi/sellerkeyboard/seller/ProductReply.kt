package com.terasumi.sellerkeyboard.seller

import java.math.BigDecimal

data class PromotionBlock(
    val discountCode: String,
    val validityNote: String,
    val terms: String = ""
)

data class ProductReply(
    val id: String,
    val name: String,
    val description: String = "",
    val price: BigDecimal? = null,
    val productUrl: String? = null,
    val imageUrl: String? = null,
    val imageSourceAuthorized: Boolean = false,
    val promotion: PromotionBlock? = null,
    val updatedAtMillis: Long
)

data class ProductReplyDraft(
    val textFallback: String,
    val imageUrl: String?,
    val imageSourceIssue: ProductImageSourceIssue?,
    val isStale: Boolean
)

object ProductReplyComposer {
    const val STALE_AFTER_DAYS = 30L

    fun compose(product: ProductReply, nowMillis: Long): ProductReplyDraft? {
        if (product.id.isBlank() || product.name.isBlank()) return null
        val lines = buildList {
            add(product.name.trim())
            product.description.trim().takeIf(String::isNotEmpty)?.let(::add)
            product.price?.let { add(SellerCalculatorEngine.formatVnd(it)) }
            product.productUrl?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
            product.promotion?.let { promotion ->
                add("Code: ${promotion.discountCode}")
                add(promotion.validityNote)
                promotion.terms.takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val staleThreshold = STALE_AFTER_DAYS * 24 * 60 * 60 * 1_000
        val imageDecision = ProductImageSourcePolicy.evaluate(
            product.imageUrl,
            product.imageSourceAuthorized
        )
        return ProductReplyDraft(
            textFallback = lines.joinToString("\n"),
            imageUrl = imageDecision.normalizedUrl,
            imageSourceIssue = imageDecision.issue,
            isStale = nowMillis - product.updatedAtMillis > staleThreshold
        )
    }
}
