package com.terasumi.sellerkeyboard.seller

import java.net.URI

enum class ProductImageSourceIssue {
    HTTPS_REQUIRED,
    HOST_REQUIRED,
    EMBEDDED_CREDENTIALS_NOT_ALLOWED,
    AUTHORIZATION_REQUIRED
}

data class ProductImageSourceDecision(
    val normalizedUrl: String?,
    val issue: ProductImageSourceIssue?
) {
    val canShare: Boolean
        get() = normalizedUrl != null && issue == null
}

object ProductImageSourcePolicy {
    fun evaluate(imageUrl: String?, sellerAuthorized: Boolean): ProductImageSourceDecision {
        val value = imageUrl?.trim().orEmpty()
        if (value.isEmpty()) return ProductImageSourceDecision(null, null)

        val uri = runCatching { URI(value) }.getOrNull()
            ?: return ProductImageSourceDecision(null, ProductImageSourceIssue.HTTPS_REQUIRED)
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return ProductImageSourceDecision(null, ProductImageSourceIssue.HTTPS_REQUIRED)
        }
        if (uri.host.isNullOrBlank()) {
            return ProductImageSourceDecision(null, ProductImageSourceIssue.HOST_REQUIRED)
        }
        if (!uri.userInfo.isNullOrBlank()) {
            return ProductImageSourceDecision(
                null,
                ProductImageSourceIssue.EMBEDDED_CREDENTIALS_NOT_ALLOWED
            )
        }
        if (!sellerAuthorized) {
            return ProductImageSourceDecision(null, ProductImageSourceIssue.AUTHORIZATION_REQUIRED)
        }
        val ascii = uri.normalize().toASCIIString()
        return ProductImageSourceDecision(
            "https" + ascii.substring(uri.scheme.length),
            null
        )
    }
}
