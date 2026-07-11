package de.johannesrabauer.steuererklaerung2025.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationSeverity;
import de.johannesrabauer.steuererklaerung2025.support.TaxReturnFixtureFactory;
import org.junit.jupiter.api.Test;

class TaxValidationServiceTest {

    private final TaxValidationService validationService = new TaxValidationService();

    @Test
    void reports_errors_for_missing_base_data_and_income() {
        var issues = validationService.validate(TaxReturnFixtureFactory.incompleteSingleReturn());

        assertThat(issues).extracting(issue -> issue.getSeverity().name()).contains("ERROR", "WARNING");
        assertThat(issues).anyMatch(issue -> issue.getFieldPath().equals("base.taxpayer"));
        assertThat(issues).anyMatch(issue -> issue.getFieldPath().equals("income.records"));
    }

    @Test
    void complete_fixture_is_export_ready_without_errors() {
        var issues = validationService.validate(TaxReturnFixtureFactory.completeJointReturn());

        assertThat(issues).allMatch(issue -> issue.getSeverity() != ValidationSeverity.ERROR);
    }
}
