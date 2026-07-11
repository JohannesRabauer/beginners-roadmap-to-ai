package de.johannesrabauer.steuererklaerung2025.application;

import de.johannesrabauer.steuererklaerung2025.domain.model.DeductionItemEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.FilingMode;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonRole;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationIssueEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationSeverity;
import de.johannesrabauer.steuererklaerung2025.domain.model.WageTaxCertificateEntity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class TaxValidationService {

    public List<ValidationIssueEntity> validate(TaxReturnEntity taxReturn) {
        List<ValidationIssueEntity> issues = new ArrayList<>();

        validatePerson(taxReturn.findPerson(PersonRole.TAXPAYER).orElse(null), "base.taxpayer", "die steuerpflichtige Person", issues);
        if (taxReturn.getFilingMode() == FilingMode.JOINT) {
            validatePerson(taxReturn.findPerson(PersonRole.SPOUSE).orElse(null), "base.spouse", "die zweite Person bei gemeinsamer Veranlagung", issues);
        }

        requireText(taxReturn.getStreetAddress(), "base.address.street", "Bitte geben Sie die Strasse und Hausnummer an.", issues);
        requireText(taxReturn.getPostalCode(), "base.address.postalCode", "Bitte geben Sie die Postleitzahl an.", issues);
        requireText(taxReturn.getCity(), "base.address.city", "Bitte geben Sie den Ort an.", issues);
        requireText(taxReturn.getTaxOffice(), "base.taxOffice", "Bitte geben Sie das zustaendige Finanzamt an.", issues);
        warnIfBlank(taxReturn.getIban(), "base.iban", "Eine IBAN hilft spaeter beim Erstattungsbezug.", issues);

        if (taxReturn.getWageTaxCertificates().isEmpty()) {
            addIssue(issues, ValidationSeverity.ERROR, "income.records", "Bitte erfassen Sie mindestens eine Lohnsteuerbescheinigung.");
        }

        int certificateIndex = 0;
        for (WageTaxCertificateEntity certificate : taxReturn.getWageTaxCertificates()) {
            String basePath = "income.records[%d]".formatted(certificateIndex++);
            requireText(certificate.getEmployerName(), basePath + ".employerName", "Bitte geben Sie den Arbeitgeber an.", issues);
            if (certificate.getPerson() == null) {
                addIssue(issues, ValidationSeverity.ERROR, basePath + ".personRole", "Bitte ordnen Sie die Bescheinigung einer Person zu.");
            }
            requireNonNegative(certificate.getGrossWages(), basePath + ".grossWages", "Der Bruttoarbeitslohn darf nicht negativ sein.", issues);
            requireNonNegative(certificate.getWageTax(), basePath + ".wageTax", "Die Lohnsteuer darf nicht negativ sein.", issues);
            requireNonNegative(certificate.getSolidaritySurcharge(), basePath + ".solidaritySurcharge", "Der Solidaritaetszuschlag darf nicht negativ sein.", issues);
            requireNonNegative(certificate.getChurchTax(), basePath + ".churchTax", "Die Kirchensteuer darf nicht negativ sein.", issues);
        }

        if (taxReturn.getFilingMode() == FilingMode.JOINT && taxReturn.getWageTaxCertificates().stream()
                .noneMatch(certificate -> certificate.getPerson() != null && certificate.getPerson().getRole() == PersonRole.SPOUSE)) {
            addIssue(issues, ValidationSeverity.WARNING, "income.spouse", "Falls die zweite Person Einkuenfte hatte, erfassen Sie mindestens eine Bescheinigung.");
        }

        int deductionIndex = 0;
        for (DeductionItemEntity deductionItem : taxReturn.getDeductionItems()) {
            String basePath = "deduction.items[%d]".formatted(deductionIndex++);
            requireText(deductionItem.getCategory(), basePath + ".category", "Eine Ausgabenkategorie fehlt.", issues);
            requireNonNegative(deductionItem.getAmount(), basePath + ".amount", "Ein Betrag darf nicht negativ sein.", issues);
            if (deductionItem.getAmount() != null
                    && deductionItem.getAmount().compareTo(BigDecimal.ZERO) > 0
                    && (deductionItem.getEvidenceStatus() == null || deductionItem.getEvidenceStatus().isBlank())) {
                addIssue(issues, ValidationSeverity.WARNING, basePath + ".evidenceStatus", "Bitte geben Sie an, ob ein Beleg vorliegt.");
            }
        }

        if (hasNoRecordedAmounts(taxReturn, TaxCalculationService.CATEGORY_WORK_EXPENSES)) {
            addIssue(issues, ValidationSeverity.WARNING, "workExpenses.section", "Es wurden noch keine Werbungskosten erfasst. Ohne Angaben greift nur der Pauschbetrag.");
        }
        if (hasNoRecordedAmounts(taxReturn, TaxCalculationService.CATEGORY_INSURANCE)) {
            addIssue(issues, ValidationSeverity.WARNING, "insurance.section", "Ohne Vorsorgeaufwand fehlen moegliche abzugsfaehige Versicherungsbeitraege.");
        }
        if (hasNoRecordedAmounts(taxReturn, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES)) {
            addIssue(issues, ValidationSeverity.WARNING, "specialExpenses.section", "Es wurden noch keine Sonderausgaben erfasst.");
        }

        return issues;
    }

    public boolean hasBlockingErrors(List<ValidationIssueEntity> validationIssues) {
        return validationIssues.stream().anyMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR);
    }

    private boolean hasNoRecordedAmounts(TaxReturnEntity taxReturn, String category) {
        return taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()))
                .map(DeductionItemEntity::getAmount)
                .allMatch(amount -> amount == null || amount.compareTo(BigDecimal.ZERO) == 0);
    }

    private void validatePerson(PersonEntity person, String fieldPrefix, String personLabel, List<ValidationIssueEntity> issues) {
        if (person == null) {
            addIssue(issues, ValidationSeverity.ERROR, fieldPrefix, "Bitte erfassen Sie " + personLabel + ".");
            return;
        }
        requireText(person.getFirstName(), fieldPrefix + ".firstName", "Bitte geben Sie den Vornamen fuer " + personLabel + " an.", issues);
        requireText(person.getLastName(), fieldPrefix + ".lastName", "Bitte geben Sie den Nachnamen fuer " + personLabel + " an.", issues);
        requireText(person.getTaxId(), fieldPrefix + ".taxId", "Bitte geben Sie die Steuer-ID fuer " + personLabel + " an.", issues);
        requireText(person.getMaritalStatus(), fieldPrefix + ".maritalStatus", "Bitte waehlen Sie den Familienstand fuer " + personLabel + ".", issues);
    }

    private void requireText(String value, String fieldPath, String message, List<ValidationIssueEntity> issues) {
        if (value == null || value.isBlank()) {
            addIssue(issues, ValidationSeverity.ERROR, fieldPath, message);
        }
    }

    private void warnIfBlank(String value, String fieldPath, String message, List<ValidationIssueEntity> issues) {
        if (value == null || value.isBlank()) {
            addIssue(issues, ValidationSeverity.WARNING, fieldPath, message);
        }
    }

    private void requireNonNegative(BigDecimal value, String fieldPath, String message, List<ValidationIssueEntity> issues) {
        if (value == null) {
            addIssue(issues, ValidationSeverity.ERROR, fieldPath, "Bitte geben Sie einen Betrag ein.");
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            addIssue(issues, ValidationSeverity.ERROR, fieldPath, message);
        }
    }

    private void addIssue(List<ValidationIssueEntity> issues, ValidationSeverity severity, String fieldPath, String message) {
        ValidationIssueEntity validationIssue = new ValidationIssueEntity();
        validationIssue.setSeverity(severity);
        validationIssue.setFieldPath(fieldPath.toLowerCase(Locale.ROOT));
        validationIssue.setMessage(message);
        issues.add(validationIssue);
    }
}
