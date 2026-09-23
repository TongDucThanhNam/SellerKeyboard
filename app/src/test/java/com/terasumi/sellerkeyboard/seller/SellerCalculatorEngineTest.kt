package com.terasumi.sellerkeyboard.seller

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SellerCalculatorEngineTest {
    @Test
    fun deterministicCommercialFormulas() {
        assertEquals("80000", SellerCalculatorEngine.discount(bd("100000"), bd("20")).toPlainString())
        assertEquals("125000", SellerCalculatorEngine.markup(bd("100000"), bd("25")).toPlainString())
        assertEquals("125000.00", SellerCalculatorEngine.priceForMargin(bd("100000"), bd("20"))?.toPlainString())
        assertEquals("7500", SellerCalculatorEngine.platformFee(bd("150000"), bd("5")).toPlainString())
        assertEquals("10000.00", SellerCalculatorEngine.shippingPerItem(bd("30000"), 3)?.toPlainString())
        assertEquals("450000", SellerCalculatorEngine.quantityTotal(3, bd("150000"))?.toPlainString())
        assertEquals(
            bd("65000"),
            SellerCalculatorEngine.profitEstimate(bd("150000"), bd("60000"), bd("10000"), bd("15000"))
        )
    }

    @Test
    fun invalidDivisorsAndLocaleFormatting_arePredictable() {
        assertNull(SellerCalculatorEngine.priceForMargin(bd("100"), bd("100")))
        assertNull(SellerCalculatorEngine.shippingPerItem(bd("100"), 0))
        assertNull(SellerCalculatorEngine.quantityTotal(-1, bd("100")))
        assertEquals("1.234.568₫", SellerCalculatorEngine.formatVnd(bd("1234567.5")))
        assertEquals("-12.500₫", SellerCalculatorEngine.formatVnd(bd("-12500")))
    }

    private fun bd(value: String) = BigDecimal(value)
}
