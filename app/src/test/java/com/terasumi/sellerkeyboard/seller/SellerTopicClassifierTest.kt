package com.terasumi.sellerkeyboard.seller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SellerTopicClassifierTest {
    @Test
    fun explicitTextMapsToTrustedTopic_orReturnsNoSpeculation() {
        assertEquals(
            SellerTopic.ORDER_STATUS_TRACKING,
            SellerTopicClassifier.classifyExplicitText("Cho mình xin mã vận đơn")?.topic
        )
        assertEquals(
            SellerTopic.PRICE_DISCOUNT,
            SellerTopicClassifier.classifyExplicitText("Sản phẩm này có giảm giá không?")?.topic
        )
        assertNull(SellerTopicClassifier.classifyExplicitText("Nội dung không rõ ràng"))
    }
}
