package com.buyless.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Remembered categories only work if the same merchant always maps to the same key, however the
// bank happens to space or capitalise the name in a given alert.
class CategoryRulesTest {

    @Test
    fun sameMerchantWithDifferentSpacingAndCaseMatches() {
        assertEquals(CategoryRules.key("Grab Holdings"), CategoryRules.key("  GRAB   holdings "))
    }

    @Test
    fun differentMerchantsDoNotMatch() {
        assert(CategoryRules.key("Grab") != CategoryRules.key("GrabFood"))
    }

    @Test
    fun blankMerchantGetsNoRule() {
        assertNull(CategoryRules.key("   "))
    }
}
