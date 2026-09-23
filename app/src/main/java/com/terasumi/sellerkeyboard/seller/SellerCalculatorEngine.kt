package com.terasumi.sellerkeyboard.seller

import java.math.BigDecimal
import java.math.RoundingMode

object SellerCalculatorEngine {
    fun discount(price: BigDecimal, percent: BigDecimal): BigDecimal =
        price.multiply(BigDecimal.ONE - percent.rate()).money()

    fun markup(cost: BigDecimal, percent: BigDecimal): BigDecimal =
        cost.multiply(BigDecimal.ONE + percent.rate()).money()

    fun priceForMargin(cost: BigDecimal, marginPercent: BigDecimal): BigDecimal? {
        val divisor = BigDecimal.ONE - marginPercent.rate()
        if (divisor <= BigDecimal.ZERO) return null
        return cost.divide(divisor, 2, RoundingMode.HALF_UP)
    }

    fun platformFee(gross: BigDecimal, feePercent: BigDecimal): BigDecimal =
        gross.multiply(feePercent.rate()).money()

    fun shippingPerItem(totalShipping: BigDecimal, quantity: Int): BigDecimal? =
        if (quantity <= 0) null else totalShipping.divide(quantity.toBigDecimal(), 2, RoundingMode.HALF_UP)

    fun quantityTotal(quantity: Int, unitPrice: BigDecimal): BigDecimal? =
        if (quantity < 0) null else unitPrice.multiply(quantity.toBigDecimal()).money()

    fun profitEstimate(
        revenue: BigDecimal,
        productCost: BigDecimal,
        platformFee: BigDecimal,
        shippingCost: BigDecimal
    ): BigDecimal = revenue - productCost - platformFee - shippingCost

    fun formatVnd(value: BigDecimal): String {
        val rounded = value.setScale(0, RoundingMode.HALF_UP).toPlainString()
        val negative = rounded.startsWith('-')
        val digits = rounded.removePrefix("-")
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return (if (negative) "-" else "") + grouped + "₫"
    }

    private fun BigDecimal.rate(): BigDecimal = divide(BigDecimal(100), 8, RoundingMode.HALF_UP)
    private fun BigDecimal.money(): BigDecimal = setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
}
