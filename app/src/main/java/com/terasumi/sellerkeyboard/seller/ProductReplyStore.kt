package com.terasumi.sellerkeyboard.seller

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

interface ProductReplyStore {
    fun list(): List<ProductReply>
    fun save(product: ProductReply)
    fun delete(id: String)
}

class SharedPreferencesProductReplyStore(context: Context) : ProductReplyStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun list(): List<ProductReply> = ProductReplyPersistenceCodec.decode(
        preferences.getString(KEY_PRODUCTS, null)
    )

    override fun save(product: ProductReply) {
        require(product.id.isNotBlank() && product.name.isNotBlank())
        val imageDecision = ProductImageSourcePolicy.evaluate(
            product.imageUrl,
            product.imageSourceAuthorized
        )
        require(imageDecision.issue == null) {
            "Product image source must be authorized and use an HTTPS URL"
        }
        val normalizedProduct = product.copy(
            imageUrl = imageDecision.normalizedUrl,
            imageSourceAuthorized = imageDecision.normalizedUrl != null
        )
        val updated = list().filterNot { it.id == product.id } + normalizedProduct
        preferences.edit().putString(KEY_PRODUCTS, ProductReplyPersistenceCodec.encode(updated)).apply()
    }

    override fun delete(id: String) {
        preferences.edit().putString(
            KEY_PRODUCTS,
            ProductReplyPersistenceCodec.encode(list().filterNot { it.id == id })
        ).apply()
    }

    companion object {
        private const val PREFS_NAME = "product_replies"
        private const val KEY_PRODUCTS = "products"
    }
}

internal object ProductReplyPersistenceCodec {
    private val gson = Gson()

    fun decode(json: String?): List<ProductReply> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val stored = gson.fromJson<List<StoredProductReply>>(
                json,
                object : TypeToken<List<StoredProductReply>>() {}.type
            ).orEmpty()
            stored.mapNotNull(StoredProductReply::toProductReply)
        }.getOrDefault(emptyList())
    }

    fun encode(products: List<ProductReply>): String = gson.toJson(products)
}

private data class StoredProductReply(
    val id: String?,
    val name: String?,
    val description: String?,
    val price: java.math.BigDecimal?,
    val productUrl: String?,
    val imageUrl: String?,
    val imageSourceAuthorized: Boolean?,
    val promotion: StoredPromotionBlock?,
    val updatedAtMillis: Long?
) {
    fun toProductReply(): ProductReply? {
        val requiredId = id ?: return null
        val requiredName = name ?: return null
        return ProductReply(
            id = requiredId,
            name = requiredName,
            description = description.orEmpty(),
            price = price,
            productUrl = productUrl,
            imageUrl = imageUrl,
            imageSourceAuthorized = imageSourceAuthorized ?: false,
            promotion = promotion?.toPromotionBlock(),
            updatedAtMillis = updatedAtMillis ?: 0L
        )
    }
}

private data class StoredPromotionBlock(
    val discountCode: String?,
    val validityNote: String?,
    val terms: String?
) {
    fun toPromotionBlock(): PromotionBlock = PromotionBlock(
        discountCode = discountCode.orEmpty(),
        validityNote = validityNote.orEmpty(),
        terms = terms.orEmpty()
    )
}
