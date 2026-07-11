package de.johannesrabauer.steuererklaerung2025.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.johannesrabauer.steuererklaerung2025.support.TaxReturnFixtureFactory;
import org.junit.jupiter.api.Test;

class TaxCalculationServiceTest {

    private final TaxCalculationService calculationService = new TaxCalculationService();

    @Test
    void calculates_expected_v1_totals_and_traceability() {
        var summary = calculationService.calculate(TaxReturnFixtureFactory.completeJointReturn());

        assertThat(summary.grossWages()).isEqualByComparingTo("82000.00");
        assertThat(summary.deductibleWorkExpenses()).isEqualByComparingTo("3640.00");
        assertThat(summary.insuranceContributions()).isEqualByComparingTo("11550.00");
        assertThat(summary.specialExpenses()).isEqualByComparingTo("700.00");
        assertThat(summary.extraordinaryBurdens()).isEqualByComparingTo("700.00");
        assertThat(summary.householdTaxRelief()).isEqualByComparingTo("240.00");
        assertThat(summary.craftsmenTaxRelief()).isEqualByComparingTo("160.00");
        assertThat(summary.estimatedTaxableIncome()).isEqualByComparingTo("65410.00");
        assertThat(summary.traces()).extracting(TaxCalculationService.RuleTrace::ruleCode)
                .contains("EINKUENFTE_BRUTTO_2025", "VORAUSSICHTLICHE_BEMESSUNGSGRUNDLAGE_2025");
    }
}
