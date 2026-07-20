package com.smartapply.ida.module.app.billing.support;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingCostCalculatorTest {

    @Test
    void pointsFor_nullUsage_shouldChargeMinimum() {
        assertEquals(1, BillingCostCalculator.pointsFor(null, 1000, 1));
    }

    @Test
    void pointsFor_zeroTokens_shouldChargeMinimum() {
        TokenUsage usage = new TokenUsage(0, 0);
        assertEquals(1, BillingCostCalculator.pointsFor(usage, 1000, 1));
    }

    @Test
    void pointsFor_exactBoundary_shouldChargeOne() {
        TokenUsage usage = new TokenUsage(500, 500);
        assertEquals(1, BillingCostCalculator.pointsFor(usage, 1000, 1));
    }

    @Test
    void pointsFor_justOverBoundary_shouldRoundUp() {
        TokenUsage usage = new TokenUsage(600, 500);
        assertEquals(2, BillingCostCalculator.pointsFor(usage, 1000, 1));
    }

    @Test
    void pointsFor_respectsCustomMinimum() {
        TokenUsage usage = new TokenUsage(100, 100);
        assertEquals(3, BillingCostCalculator.pointsFor(usage, 1000, 3));
    }

    @Test
    void totalTokens_prefersTotalCount() {
        TokenUsage usage = new TokenUsage(100, 200, 350);
        assertEquals(350, BillingCostCalculator.totalTokens(usage));
    }

    @Test
    void totalTokens_fallsBackToSum() {
        TokenUsage usage = new TokenUsage(100, 250);
        assertEquals(350, BillingCostCalculator.totalTokens(usage));
    }
}
