package de.johannesrabauer.steuererklaerung2025.application;

import de.johannesrabauer.steuererklaerung2025.domain.model.DeductionItemEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.FilingMode;
import de.johannesrabauer.steuererklaerung2025.domain.model.InterviewStep;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonRole;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationIssueEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationSeverity;
import de.johannesrabauer.steuererklaerung2025.domain.model.WageTaxCertificateEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TaxCalculationService {

    public static final String CATEGORY_WORK_EXPENSES = "WERBUNGSKOSTEN";
    public static final String CATEGORY_INSURANCE = "VORSORGE";
    public static final String CATEGORY_SPECIAL_EXPENSES = "SONDERAUSGABEN";
    public static final String CATEGORY_HOUSEHOLD = "HAUSHALT";
    public static final String CATEGORY_EXTRAORDINARY = "BELASTUNGEN";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final BigDecimal EMPLOYEE_ALLOWANCE_2025 = new BigDecimal("1230.00");
    private static final BigDecimal HOUSEHOLD_RELIEF_CAP = new BigDecimal("4000.00");
    private static final BigDecimal CRAFTSMAN_RELIEF_CAP = new BigDecimal("1200.00");
    private static final BigDecimal TAX_RELIEF_RATE = new BigDecimal("0.20");

    public CalculationSummary calculate(TaxReturnEntity taxReturn) {
        List<RuleTrace> traces = new ArrayList<>();

        BigDecimal grossWages = sumCertificates(taxReturn, WageTaxCertificateEntity::getGrossWages);
        traces.add(new RuleTrace(
                "EINKUENFTE_BRUTTO_2025",
                "Summe der Bruttoarbeitsloehne aus allen erfassten Lohnsteuerbescheinigungen.",
                taxReturn.getWageTaxCertificates().stream()
                        .map(certificate -> "income." + personRoleKey(certificate.getPerson()) + "." + safe(certificate.getEmployerName()))
                        .toList(),
                grossWages));

        BigDecimal wageTax = sumCertificates(taxReturn, WageTaxCertificateEntity::getWageTax);
        traces.add(new RuleTrace(
                "LOHNSTEUER_BESCHEINIGUNG_2025",
                "Summe der bereits einbehaltenen Lohnsteuer laut Bescheinigungen.",
                taxReturn.getWageTaxCertificates().stream()
                        .map(certificate -> "income." + personRoleKey(certificate.getPerson()) + ".wageTax")
                        .toList(),
                wageTax));

        BigDecimal solidaritySurcharge = sumCertificates(taxReturn, WageTaxCertificateEntity::getSolidaritySurcharge);
        traces.add(new RuleTrace(
                "SOLI_BESCHEINIGUNG_2025",
                "Summe des Solidaritaetszuschlags laut Bescheinigungen.",
                taxReturn.getWageTaxCertificates().stream()
                        .map(certificate -> "income." + personRoleKey(certificate.getPerson()) + ".solidaritySurcharge")
                        .toList(),
                solidaritySurcharge));

        BigDecimal churchTax = sumCertificates(taxReturn, WageTaxCertificateEntity::getChurchTax);
        traces.add(new RuleTrace(
                "KIRCHENSTEUER_BESCHEINIGUNG_2025",
                "Summe der Kirchensteuer aus den Bescheinigungen.",
                taxReturn.getWageTaxCertificates().stream()
                        .map(certificate -> "income." + personRoleKey(certificate.getPerson()) + ".churchTax")
                        .toList(),
                churchTax));

        BigDecimal deductibleWorkExpenses = calculateWorkExpenseDeduction(taxReturn, traces);
        BigDecimal insuranceContributions = sumCategory(taxReturn, CATEGORY_INSURANCE);
        traces.add(new RuleTrace(
                "VORSORGEAUFWAND_SUMME_2025",
                "Summe der unterstuetzten Versicherungs- und Rentenbeitraege.",
                deductionPaths(taxReturn, CATEGORY_INSURANCE),
                insuranceContributions));

        BigDecimal specialExpenses = sumCategory(taxReturn, CATEGORY_SPECIAL_EXPENSES);
        traces.add(new RuleTrace(
                "SONDERAUSGABEN_SUMME_2025",
                "Summe der unterstuetzten Sonderausgaben in v1.",
                deductionPaths(taxReturn, CATEGORY_SPECIAL_EXPENSES),
                specialExpenses));

        BigDecimal extraordinaryBurdens = sumCategory(taxReturn, CATEGORY_EXTRAORDINARY);
        traces.add(new RuleTrace(
                "BELASTUNGEN_SUMME_2025",
                "Summe der in v1 erfassten aussergewoehnlichen Belastungen.",
                deductionPaths(taxReturn, CATEGORY_EXTRAORDINARY),
                extraordinaryBurdens));

        BigDecimal householdTaxRelief = calculateCappedTaxRelief(
                taxReturn,
                CATEGORY_HOUSEHOLD,
                "HAUSHALTSDIENSTLEISTUNGEN",
                HOUSEHOLD_RELIEF_CAP,
                "HAUSHALTSNAHE_STEUERERMAESSIGUNG_2025",
                "20 Prozent der haushaltsnahen Dienstleistungen, gedeckelt auf 4.000 EUR.",
                traces);

        BigDecimal craftsmenTaxRelief = calculateCappedTaxRelief(
                taxReturn,
                CATEGORY_HOUSEHOLD,
                "HANDWERKERLEISTUNGEN",
                CRAFTSMAN_RELIEF_CAP,
                "HANDWERKER_STEUERERMAESSIGUNG_2025",
                "20 Prozent der unterstuetzten Handwerkerleistungen, gedeckelt auf 1.200 EUR.",
                traces);

        BigDecimal estimatedTaxableIncome = grossWages
                .subtract(deductibleWorkExpenses)
                .subtract(insuranceContributions)
                .subtract(specialExpenses)
                .subtract(extraordinaryBurdens)
                .max(ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        traces.add(new RuleTrace(
                "VORAUSSICHTLICHE_BEMESSUNGSGRUNDLAGE_2025",
                "Bruttoarbeitslohn abzueglich der in v1 berechneten abzugsfaehigen Betraege.",
                List.of(
                        "calculation.grossWages",
                        "calculation.workExpenses",
                        "calculation.insurance",
                        "calculation.specialExpenses",
                        "calculation.extraordinaryBurdens"),
                estimatedTaxableIncome));

        return new CalculationSummary(
                grossWages,
                wageTax,
                solidaritySurcharge,
                churchTax,
                deductibleWorkExpenses,
                insuranceContributions,
                specialExpenses,
                extraordinaryBurdens,
                householdTaxRelief,
                craftsmenTaxRelief,
                estimatedTaxableIncome,
                traces);
    }

    public ReviewSummary buildReviewSummary(TaxReturnEntity taxReturn, List<ValidationIssueEntity> validationIssues) {
        CalculationSummary calculation = calculate(taxReturn);
        List<ReviewSection> sections = new ArrayList<>();

        PersonEntity taxpayer = taxReturn.findPerson(PersonRole.TAXPAYER).orElse(null);
        PersonEntity spouse = taxReturn.findPerson(PersonRole.SPOUSE).orElse(null);
        sections.add(new ReviewSection(
                "hauptvordruck",
                "Hauptvordruck / Basisdaten",
                InterviewStep.BASE_DATA,
                List.of(
                        "Veranlagung: " + taxReturn.getFilingMode().getGermanLabel(),
                        "Steuerpflichtige Person: " + formatPerson(taxpayer),
                        taxReturn.getFilingMode() == FilingMode.JOINT
                                ? "Ehepartner/in: " + formatPerson(spouse)
                                : "Ehepartner/in: nicht erforderlich",
                        "Adresse: " + joinNonBlank(List.of(taxReturn.getStreetAddress(), joinNonBlank(List.of(taxReturn.getPostalCode(), taxReturn.getCity())))),
                        "Finanzamt: " + fallback(taxReturn.getTaxOffice()),
                        "IBAN: " + fallback(taxReturn.getIban()))));

        List<String> incomeLines = taxReturn.getWageTaxCertificates().stream()
                .sorted(Comparator.comparing(certificate -> personRoleKey(certificate.getPerson())))
                .map(certificate -> "%s - %s: Brutto %s EUR, Lohnsteuer %s EUR".formatted(
                        germanRoleLabel(certificate.getPerson()),
                        fallback(certificate.getEmployerName()),
                        money(certificate.getGrossWages()),
                        money(certificate.getWageTax())))
                .toList();
        sections.add(new ReviewSection(
                "anlage-n",
                "Anlage N / Einkuenfte",
                InterviewStep.INCOME,
                incomeLines.isEmpty() ? List.of("Noch keine Lohnsteuerbescheinigung erfasst.") : incomeLines));

        sections.add(new ReviewSection(
                "werbungskosten",
                "Anlage N / Werbungskosten",
                InterviewStep.WORK_EXPENSES,
                describeCategory(taxReturn, CATEGORY_WORK_EXPENSES)));
        sections.add(new ReviewSection(
                "vorsorge",
                "Vorsorgeaufwand",
                InterviewStep.INSURANCE,
                describeCategory(taxReturn, CATEGORY_INSURANCE)));
        sections.add(new ReviewSection(
                "sonderausgaben",
                "Sonderausgaben",
                InterviewStep.SPECIAL_EXPENSES,
                describeCategory(taxReturn, CATEGORY_SPECIAL_EXPENSES)));
        sections.add(new ReviewSection(
                "haushalt",
                "Haushaltsnahe Dienstleistungen",
                InterviewStep.HOUSEHOLD_SERVICES,
                describeCategory(taxReturn, CATEGORY_HOUSEHOLD)));
        sections.add(new ReviewSection(
                "belastungen",
                "Aussergewoehnliche Belastungen",
                InterviewStep.EXTRAORDINARY_BURDENS,
                describeCategory(taxReturn, CATEGORY_EXTRAORDINARY)));
        sections.add(new ReviewSection(
                "final",
                "Finale Zusammenfassung",
                InterviewStep.REVIEW,
                List.of(
                        "Bruttoarbeitslohn gesamt: " + money(calculation.grossWages()) + " EUR",
                        "Abzugsfaehige Werbungskosten: " + money(calculation.deductibleWorkExpenses()) + " EUR",
                        "Vorsorgeaufwand: " + money(calculation.insuranceContributions()) + " EUR",
                        "Sonderausgaben: " + money(calculation.specialExpenses()) + " EUR",
                        "Aussergewoehnliche Belastungen: " + money(calculation.extraordinaryBurdens()) + " EUR",
                        "Steuerermaessigung Haushalt/Handwerk: " + money(calculation.householdTaxRelief().add(calculation.craftsmenTaxRelief())) + " EUR",
                        "Voraussichtliche Bemessungsgrundlage: " + money(calculation.estimatedTaxableIncome()) + " EUR")));

        long errorCount = validationIssues.stream().filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR).count();
        long warningCount = validationIssues.size() - errorCount;

        return new ReviewSummary(calculation, sections, errorCount, warningCount, errorCount == 0);
    }

    private BigDecimal calculateWorkExpenseDeduction(TaxReturnEntity taxReturn, List<RuleTrace> traces) {
        BigDecimal result = ZERO;
        for (PersonRole role : relevantRoles(taxReturn.getFilingMode())) {
            BigDecimal enteredAmount = sumCategoryForRole(taxReturn, CATEGORY_WORK_EXPENSES, role);
            boolean hasIncome = taxReturn.getWageTaxCertificates().stream()
                    .anyMatch(certificate -> certificate.getPerson() != null && certificate.getPerson().getRole() == role);
            BigDecimal deductible = hasIncome ? enteredAmount.max(EMPLOYEE_ALLOWANCE_2025) : enteredAmount;
            result = result.add(deductible);
            traces.add(new RuleTrace(
                    "WERBUNGSKOSTEN_%s_2025".formatted(role.name()),
                    "Werbungskosten fuer %s: hoechster Wert aus erfassten Kosten und Arbeitnehmer-Pauschbetrag von 1.230 EUR, sobald Einkuenfte vorliegen."
                            .formatted(germanRoleLabel(role)),
                    deductionPaths(taxReturn, CATEGORY_WORK_EXPENSES, role),
                    deductible));
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCappedTaxRelief(
            TaxReturnEntity taxReturn,
            String category,
            String subcategory,
            BigDecimal cap,
            String ruleCode,
            String description,
            List<RuleTrace> traces) {
        BigDecimal amount = sumCategory(taxReturn, category, subcategory);
        BigDecimal relief = amount.multiply(TAX_RELIEF_RATE).min(cap).setScale(2, RoundingMode.HALF_UP);
        traces.add(new RuleTrace(
                ruleCode,
                description,
                deductionPaths(taxReturn, category, subcategory),
                relief));
        return relief;
    }

    private List<String> describeCategory(TaxReturnEntity taxReturn, String category) {
        List<String> lines = taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()))
                .sorted(Comparator.comparing(item -> safe(item.getSubcategory())))
                .map(item -> "%s - %s: %s EUR (%s)".formatted(
                        item.getPerson() == null ? "Gemeinsam" : germanRoleLabel(item.getPerson()),
                        friendlySubcategory(item.getSubcategory()),
                        money(item.getAmount()),
                        fallback(item.getEvidenceStatus())))
                .toList();
        return lines.isEmpty() ? List.of("Keine Daten erfasst.") : lines;
    }

    private BigDecimal sumCertificates(TaxReturnEntity taxReturn, java.util.function.Function<WageTaxCertificateEntity, BigDecimal> extractor) {
        return taxReturn.getWageTaxCertificates().stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumCategory(TaxReturnEntity taxReturn, String category) {
        return taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()))
                .map(DeductionItemEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumCategory(TaxReturnEntity taxReturn, String category, String subcategory) {
        return taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()) && subcategory.equals(item.getSubcategory()))
                .map(DeductionItemEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumCategoryForRole(TaxReturnEntity taxReturn, String category, PersonRole role) {
        return taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()))
                .filter(item -> item.getPerson() != null && item.getPerson().getRole() == role)
                .map(DeductionItemEntity::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> deductionPaths(TaxReturnEntity taxReturn, String category) {
        return taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()))
                .map(item -> "deduction." + category.toLowerCase(Locale.ROOT) + "." + safe(item.getSubcategory()))
                .toList();
    }

    private List<String> deductionPaths(TaxReturnEntity taxReturn, String category, PersonRole role) {
        return taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()))
                .filter(item -> item.getPerson() != null && item.getPerson().getRole() == role)
                .map(item -> "deduction." + personRoleKey(item.getPerson()) + "." + safe(item.getSubcategory()))
                .toList();
    }

    private List<String> deductionPaths(TaxReturnEntity taxReturn, String category, String subcategory) {
        return taxReturn.getDeductionItems().stream()
                .filter(item -> category.equals(item.getCategory()) && subcategory.equals(item.getSubcategory()))
                .map(item -> "deduction." + category.toLowerCase(Locale.ROOT) + "." + subcategory.toLowerCase(Locale.ROOT))
                .toList();
    }

    private List<PersonRole> relevantRoles(FilingMode filingMode) {
        return filingMode == FilingMode.JOINT
                ? List.of(PersonRole.TAXPAYER, PersonRole.SPOUSE)
                : List.of(PersonRole.TAXPAYER);
    }

    private String formatPerson(PersonEntity person) {
        if (person == null) {
            return "noch nicht vollstaendig";
        }
        return joinNonBlank(List.of(person.getFirstName(), person.getLastName(), person.getTaxId()));
    }

    private String germanRoleLabel(PersonEntity person) {
        return person == null ? "Gemeinsam" : germanRoleLabel(person.getRole());
    }

    private String germanRoleLabel(PersonRole role) {
        return role == PersonRole.SPOUSE ? "Ehepartner/in" : "Steuerpflichtige Person";
    }

    private String personRoleKey(PersonEntity person) {
        return person == null ? "gemeinsam" : personRoleKey(person.getRole());
    }

    private String personRoleKey(PersonRole role) {
        return role == PersonRole.SPOUSE ? "spouse" : "taxpayer";
    }

    private String friendlySubcategory(String subcategory) {
        if (subcategory == null) {
            return "Allgemein";
        }
        return subcategory.replace('_', ' ');
    }

    private String joinNonBlank(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String fallback(String value) {
        return value == null || value.isBlank() ? "nicht angegeben" : value;
    }

    private String safe(String value) {
        return value == null || value.isBlank()
                ? "leer"
                : value.toLowerCase(Locale.ROOT).replace(' ', '-');
    }

    private String money(BigDecimal value) {
        return value == null ? "0,00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    public record CalculationSummary(
            BigDecimal grossWages,
            BigDecimal wageTax,
            BigDecimal solidaritySurcharge,
            BigDecimal churchTax,
            BigDecimal deductibleWorkExpenses,
            BigDecimal insuranceContributions,
            BigDecimal specialExpenses,
            BigDecimal extraordinaryBurdens,
            BigDecimal householdTaxRelief,
            BigDecimal craftsmenTaxRelief,
            BigDecimal estimatedTaxableIncome,
            List<RuleTrace> traces) {
    }

    public record RuleTrace(String ruleCode, String description, List<String> inputPaths, BigDecimal result) {
    }

    public record ReviewSection(String id, String title, InterviewStep sourceStep, List<String> lines) {
    }

    public record ReviewSummary(
            CalculationSummary calculation,
            List<ReviewSection> sections,
            long errorCount,
            long warningCount,
            boolean exportReady) {
    }
}
