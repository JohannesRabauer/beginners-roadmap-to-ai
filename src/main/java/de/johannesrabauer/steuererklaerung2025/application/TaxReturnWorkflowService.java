package de.johannesrabauer.steuererklaerung2025.application;

import de.johannesrabauer.steuererklaerung2025.domain.model.DeductionItemEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ExportBundleEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.FilingMode;
import de.johannesrabauer.steuererklaerung2025.domain.model.InterviewStep;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonRole;
import de.johannesrabauer.steuererklaerung2025.domain.model.ReturnStatus;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationIssueEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationSeverity;
import de.johannesrabauer.steuererklaerung2025.domain.model.WageTaxCertificateEntity;
import de.johannesrabauer.steuererklaerung2025.domain.port.TaxReturnRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

@Service
@Transactional
public class TaxReturnWorkflowService {

    private static final String ELSTER_SHAPE_VERSION = "2025-v1";
    private static final String EVIDENCE_NOTES_PRESENT = "BELEG_VORHANDEN";
    private static final String EVIDENCE_MISSING = "NACHREICHEN";

    private final TaxReturnRepository repository;
    private final TaxValidationService validationService;
    private final int taxYear;

    public TaxReturnWorkflowService(
            TaxReturnRepository repository,
            TaxValidationService validationService,
            @Value("${steuererklaerung.tax-year}") int taxYear) {
        this.repository = repository;
        this.validationService = validationService;
        this.taxYear = taxYear;
    }

    public TaxReturnEntity createReturn(FilingMode filingMode) {
        TaxReturnEntity taxReturn = new TaxReturnEntity();
        taxReturn.setTaxYear(taxYear);
        taxReturn.setFilingMode(filingMode);
        taxReturn.setStatus(ReturnStatus.DRAFT);
        taxReturn.setCurrentInterviewStep(InterviewStep.BASE_DATA);
        upsertPerson(taxReturn, PersonRole.TAXPAYER, "", "", "", filingMode == FilingMode.JOINT ? "VERHEIRATET" : "LEDIG");
        if (filingMode == FilingMode.JOINT) {
            upsertPerson(taxReturn, PersonRole.SPOUSE, "", "", "", "VERHEIRATET");
        }
        refreshDerivedState(taxReturn);
        return repository.save(taxReturn);
    }

    @Transactional(readOnly = true)
    public List<TaxReturnEntity> findAllByTaxYear() {
        return repository.findAllByTaxYear(taxYear);
    }

    @Transactional(readOnly = true)
    public Optional<TaxReturnEntity> findById(UUID returnId) {
        return repository.findById(returnId).filter(taxReturn -> taxReturn.getTaxYear() == taxYear);
    }

    @Transactional(readOnly = true)
    public TaxReturnEntity requireReturn(UUID returnId) {
        return findById(returnId).orElseThrow(() -> new IllegalArgumentException("Steuerfall wurde nicht gefunden."));
    }

    public TaxReturnEntity saveStep(UUID returnId, InterviewStep step, MultiValueMap<String, String> formData, InterviewStep nextStep) {
        TaxReturnEntity taxReturn = requireReturn(returnId);
        switch (step) {
            case BASE_DATA -> saveBaseData(taxReturn, formData);
            case INCOME -> saveIncome(taxReturn, formData);
            case WORK_EXPENSES -> saveWorkExpenses(taxReturn, formData);
            case INSURANCE -> saveInsurance(taxReturn, formData);
            case SPECIAL_EXPENSES -> saveSpecialExpenses(taxReturn, formData);
            case HOUSEHOLD_SERVICES -> saveHousehold(taxReturn, formData);
            case EXTRAORDINARY_BURDENS -> saveExtraordinaryBurdens(taxReturn, formData);
            case REVIEW -> {
                // Review actions are handled by dedicated controller methods.
            }
        }
        taxReturn.setCurrentInterviewStep(nextStep);
        refreshDerivedState(taxReturn);
        return repository.save(taxReturn);
    }

    public TaxReturnEntity markCompleted(UUID returnId) {
        TaxReturnEntity taxReturn = requireReturn(returnId);
        refreshDerivedState(taxReturn);
        if (validationService.hasBlockingErrors(taxReturn.getValidationIssues())) {
            throw new IllegalStateException("Der Steuerfall hat noch blockierende Fehler und kann nicht abgeschlossen werden.");
        }
        taxReturn.setStatus(ReturnStatus.COMPLETED);
        taxReturn.setCompletedAt(Instant.now());
        taxReturn.setCurrentInterviewStep(InterviewStep.REVIEW);
        return repository.save(taxReturn);
    }

    public TaxReturnEntity markDraft(UUID returnId) {
        TaxReturnEntity taxReturn = requireReturn(returnId);
        taxReturn.setStatus(ReturnStatus.DRAFT);
        taxReturn.setCompletedAt(null);
        return repository.save(taxReturn);
    }

    public void deleteReturn(UUID returnId) {
        repository.delete(requireReturn(returnId));
    }

    public TaxReturnEntity save(TaxReturnEntity taxReturn) {
        return repository.save(taxReturn);
    }

    public List<IncomeRow> buildIncomeRows(TaxReturnEntity taxReturn) {
        List<IncomeRow> rows = taxReturn.getWageTaxCertificates().stream()
                .sorted(Comparator.comparing(certificate -> certificate.getPerson() == null ? 0 : certificate.getPerson().getRole().ordinal()))
                .map(certificate -> new IncomeRow(
                        certificate.getPerson() == null ? PersonRole.TAXPAYER : certificate.getPerson().getRole(),
                        certificate.getEmployerName(),
                        decimal(certificate.getGrossWages()),
                        decimal(certificate.getWageTax()),
                        decimal(certificate.getSolidaritySurcharge()),
                        decimal(certificate.getChurchTax())))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        for (PersonRole role : relevantRoles(taxReturn.getFilingMode())) {
            rows.add(new IncomeRow(role, "", "0.00", "0.00", "0.00", "0.00"));
        }
        return rows;
    }

    public Map<String, String> deductionValues(TaxReturnEntity taxReturn, String category) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (DeductionFieldKey key : DeductionFieldKey.values()) {
            if (!category.equals(key.category())) {
                continue;
            }
            BigDecimal amount = taxReturn.getDeductionItems().stream()
                    .filter(item -> category.equals(item.getCategory()))
                    .filter(item -> key.subcategory().equals(item.getSubcategory()))
                    .filter(item -> matchesRole(item, key.role()))
                    .map(DeductionItemEntity::getAmount)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
            values.put(key.name(), decimal(amount));
        }
        return values;
    }

    public Map<String, String> evidenceValues(TaxReturnEntity taxReturn, String category) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        for (DeductionFieldKey key : DeductionFieldKey.values()) {
            if (!category.equals(key.category())) {
                continue;
            }
            String evidence = taxReturn.getDeductionItems().stream()
                    .filter(item -> category.equals(item.getCategory()))
                    .filter(item -> key.subcategory().equals(item.getSubcategory()))
                    .filter(item -> matchesRole(item, key.role()))
                    .map(DeductionItemEntity::getEvidenceStatus)
                    .findFirst()
                    .orElse(EVIDENCE_NOTES_PRESENT);
            values.put(key.name(), evidence);
        }
        return values;
    }

    public List<StepStatus> buildStepStatuses(TaxReturnEntity taxReturn) {
        List<StepStatus> statuses = new ArrayList<>();
        for (InterviewStep step : InterviewStep.values()) {
            boolean hasError = taxReturn.getValidationIssues().stream()
                    .anyMatch(issue -> issue.getSeverity() == ValidationSeverity.ERROR && belongsToStep(issue, step));
            boolean hasWarning = taxReturn.getValidationIssues().stream()
                    .anyMatch(issue -> issue.getSeverity() == ValidationSeverity.WARNING && belongsToStep(issue, step));
            String status = step == taxReturn.getCurrentInterviewStep() ? "AKTIV" : hasError ? "FEHLT" : hasWarning ? "HINWEIS" : "OK";
            statuses.add(new StepStatus(step, status));
        }
        return statuses;
    }

    private void saveBaseData(TaxReturnEntity taxReturn, MultiValueMap<String, String> formData) {
        taxReturn.setStreetAddress(readText(formData, "streetAddress"));
        taxReturn.setPostalCode(readText(formData, "postalCode"));
        taxReturn.setCity(readText(formData, "city"));
        taxReturn.setIban(readText(formData, "iban"));
        taxReturn.setBankAccountHolder(readText(formData, "bankAccountHolder"));
        taxReturn.setTaxOffice(readText(formData, "taxOffice"));

        upsertPerson(
                taxReturn,
                PersonRole.TAXPAYER,
                readText(formData, "taxpayerFirstName"),
                readText(formData, "taxpayerLastName"),
                readText(formData, "taxpayerTaxId"),
                readText(formData, "taxpayerMaritalStatus"));

        if (taxReturn.getFilingMode() == FilingMode.JOINT) {
            upsertPerson(
                    taxReturn,
                    PersonRole.SPOUSE,
                    readText(formData, "spouseFirstName"),
                    readText(formData, "spouseLastName"),
                    readText(formData, "spouseTaxId"),
                    readText(formData, "spouseMaritalStatus"));
        } else {
            taxReturn.getPersons().removeIf(person -> person.getRole() == PersonRole.SPOUSE);
            taxReturn.getWageTaxCertificates().removeIf(certificate -> certificate.getPerson() != null && certificate.getPerson().getRole() == PersonRole.SPOUSE);
            taxReturn.getDeductionItems().removeIf(item -> item.getPerson() != null && item.getPerson().getRole() == PersonRole.SPOUSE);
        }
    }

    private void saveIncome(TaxReturnEntity taxReturn, MultiValueMap<String, String> formData) {
        List<String> roles = formData.getOrDefault("incomeRole", List.of());
        List<String> employers = formData.getOrDefault("incomeEmployerName", List.of());
        List<String> grossWages = formData.getOrDefault("incomeGrossWages", List.of());
        List<String> wageTaxes = formData.getOrDefault("incomeWageTax", List.of());
        List<String> solidarity = formData.getOrDefault("incomeSolidaritySurcharge", List.of());
        List<String> church = formData.getOrDefault("incomeChurchTax", List.of());

        List<WageTaxCertificateEntity> certificates = new ArrayList<>();
        int rowCount = employers.size();
        for (int index = 0; index < rowCount; index++) {
            String employerName = valueAt(employers, index);
            BigDecimal gross = parseAmount(valueAt(grossWages, index));
            BigDecimal wageTax = parseAmount(valueAt(wageTaxes, index));
            BigDecimal solidaritySurcharge = parseAmount(valueAt(solidarity, index));
            BigDecimal churchTax = parseAmount(valueAt(church, index));
            if (isBlank(employerName) && isZero(gross) && isZero(wageTax) && isZero(solidaritySurcharge) && isZero(churchTax)) {
                continue;
            }

            WageTaxCertificateEntity certificate = new WageTaxCertificateEntity();
            PersonRole role = parseRole(valueAt(roles, index));
            certificate.setPerson(resolvePerson(taxReturn, role));
            certificate.setEmployerName(employerName);
            certificate.setGrossWages(gross);
            certificate.setWageTax(wageTax);
            certificate.setSolidaritySurcharge(solidaritySurcharge);
            certificate.setChurchTax(churchTax);
            certificates.add(certificate);
        }
        taxReturn.replaceWageTaxCertificates(certificates);
    }

    private void saveWorkExpenses(TaxReturnEntity taxReturn, MultiValueMap<String, String> formData) {
        replaceCategoryItems(
                taxReturn,
                TaxCalculationService.CATEGORY_WORK_EXPENSES,
                items(
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_WORK_EXPENSES, "PENDLERPAUSCHALE", formData, "taxpayerCommute", "taxpayerCommuteEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_WORK_EXPENSES, "REISEKOSTEN", formData, "taxpayerTravel", "taxpayerTravelEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_WORK_EXPENSES, "ARBEITSMITTEL", formData, "taxpayerEquipment", "taxpayerEquipmentEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_WORK_EXPENSES, "GEWERKSCHAFT", formData, "taxpayerUnionFees", "taxpayerUnionFeesEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_WORK_EXPENSES, "PENDLERPAUSCHALE", formData, "spouseCommute", "spouseCommuteEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_WORK_EXPENSES, "REISEKOSTEN", formData, "spouseTravel", "spouseTravelEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_WORK_EXPENSES, "ARBEITSMITTEL", formData, "spouseEquipment", "spouseEquipmentEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_WORK_EXPENSES, "GEWERKSCHAFT", formData, "spouseUnionFees", "spouseUnionFeesEvidence")));
    }

    private void saveInsurance(TaxReturnEntity taxReturn, MultiValueMap<String, String> formData) {
        replaceCategoryItems(
                taxReturn,
                TaxCalculationService.CATEGORY_INSURANCE,
                items(
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_INSURANCE, "RENTENVERSICHERUNG", formData, "taxpayerPension", "taxpayerPensionEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_INSURANCE, "KRANKENVERSICHERUNG", formData, "taxpayerHealth", "taxpayerHealthEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_INSURANCE, "ARBEITSLOSENVERSICHERUNG", formData, "taxpayerUnemployment", "taxpayerUnemploymentEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_INSURANCE, "HAFTPFLICHT", formData, "taxpayerLiability", "taxpayerLiabilityEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_INSURANCE, "RENTENVERSICHERUNG", formData, "spousePension", "spousePensionEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_INSURANCE, "KRANKENVERSICHERUNG", formData, "spouseHealth", "spouseHealthEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_INSURANCE, "ARBEITSLOSENVERSICHERUNG", formData, "spouseUnemployment", "spouseUnemploymentEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_INSURANCE, "HAFTPFLICHT", formData, "spouseLiability", "spouseLiabilityEvidence")));
    }

    private void saveSpecialExpenses(TaxReturnEntity taxReturn, MultiValueMap<String, String> formData) {
        replaceCategoryItems(
                taxReturn,
                TaxCalculationService.CATEGORY_SPECIAL_EXPENSES,
                items(
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "SPENDEN", formData, "taxpayerDonations", "taxpayerDonationsEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "KIRCHENSTEUER", formData, "taxpayerChurchTax", "taxpayerChurchTaxEvidence"),
                        itemForRole(taxReturn, PersonRole.TAXPAYER, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "WEITERBILDUNG", formData, "taxpayerEducation", "taxpayerEducationEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "SPENDEN", formData, "spouseDonations", "spouseDonationsEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "KIRCHENSTEUER", formData, "spouseChurchTax", "spouseChurchTaxEvidence"),
                        itemForOptionalRole(taxReturn, PersonRole.SPOUSE, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "WEITERBILDUNG", formData, "spouseEducation", "spouseEducationEvidence")));
    }

    private void saveHousehold(TaxReturnEntity taxReturn, MultiValueMap<String, String> formData) {
        replaceCategoryItems(
                taxReturn,
                TaxCalculationService.CATEGORY_HOUSEHOLD,
                items(
                        itemWithoutRole(TaxCalculationService.CATEGORY_HOUSEHOLD, "HAUSHALTSDIENSTLEISTUNGEN", formData, "householdServices", "householdServicesEvidence"),
                        itemWithoutRole(TaxCalculationService.CATEGORY_HOUSEHOLD, "HANDWERKERLEISTUNGEN", formData, "craftsmenServices", "craftsmenServicesEvidence")));
    }

    private void saveExtraordinaryBurdens(TaxReturnEntity taxReturn, MultiValueMap<String, String> formData) {
        replaceCategoryItems(
                taxReturn,
                TaxCalculationService.CATEGORY_EXTRAORDINARY,
                items(
                        itemWithoutRole(TaxCalculationService.CATEGORY_EXTRAORDINARY, "KRANKHEITSKOSTEN", formData, "medicalCosts", "medicalCostsEvidence"),
                        itemWithoutRole(TaxCalculationService.CATEGORY_EXTRAORDINARY, "PFLEGEKOSTEN", formData, "careCosts", "careCostsEvidence"),
                        itemWithoutRole(TaxCalculationService.CATEGORY_EXTRAORDINARY, "SONSTIGE", formData, "otherExtraordinaryCosts", "otherExtraordinaryCostsEvidence")));
    }

    private void refreshDerivedState(TaxReturnEntity taxReturn) {
        taxReturn.replaceValidationIssues(validationService.validate(taxReturn));
        ExportBundleEntity exportBundle = taxReturn.getExportBundle();
        if (exportBundle == null) {
            exportBundle = new ExportBundleEntity();
            taxReturn.setExportBundle(exportBundle);
        }
        exportBundle.setElsterShapeVersion(ELSTER_SHAPE_VERSION);
        exportBundle.setExportStatus(validationService.hasBlockingErrors(taxReturn.getValidationIssues()) ? "BLOCKED" : "READY");
    }

    private void replaceCategoryItems(TaxReturnEntity taxReturn, String category, List<DeductionItemEntity> newItems) {
        taxReturn.getDeductionItems().removeIf(item -> category.equals(item.getCategory()));
        newItems.stream()
                .filter(java.util.Objects::nonNull)
                .forEach(taxReturn::addDeductionItem);
    }

    private DeductionItemEntity itemForRole(
            TaxReturnEntity taxReturn,
            PersonRole role,
            String category,
            String subcategory,
            MultiValueMap<String, String> formData,
            String amountField,
            String evidenceField) {
        return buildDeductionItem(resolvePerson(taxReturn, role), category, subcategory, formData, amountField, evidenceField);
    }

    private DeductionItemEntity itemForOptionalRole(
            TaxReturnEntity taxReturn,
            PersonRole role,
            String category,
            String subcategory,
            MultiValueMap<String, String> formData,
            String amountField,
            String evidenceField) {
        PersonEntity person = taxReturn.findPerson(role).orElse(null);
        if (person == null) {
            return null;
        }
        return buildDeductionItem(person, category, subcategory, formData, amountField, evidenceField);
    }

    private DeductionItemEntity itemWithoutRole(
            String category,
            String subcategory,
            MultiValueMap<String, String> formData,
            String amountField,
            String evidenceField) {
        return buildDeductionItem(null, category, subcategory, formData, amountField, evidenceField);
    }

    private DeductionItemEntity buildDeductionItem(
            PersonEntity person,
            String category,
            String subcategory,
            MultiValueMap<String, String> formData,
            String amountField,
            String evidenceField) {
        BigDecimal amount = parseAmount(readText(formData, amountField));
        String evidenceStatus = readText(formData, evidenceField);
        if (isZero(amount) && isBlank(evidenceStatus)) {
            return null;
        }
        DeductionItemEntity item = new DeductionItemEntity();
        item.setPerson(person);
        item.setCategory(category);
        item.setSubcategory(subcategory);
        item.setAmount(amount);
        item.setEvidenceStatus(isBlank(evidenceStatus) ? EVIDENCE_NOTES_PRESENT : evidenceStatus);
        return item;
    }

    private void upsertPerson(
            TaxReturnEntity taxReturn,
            PersonRole role,
            String firstName,
            String lastName,
            String taxId,
            String maritalStatus) {
        PersonEntity person = taxReturn.findPerson(role).orElseGet(() -> {
            PersonEntity created = new PersonEntity();
            created.setRole(role);
            taxReturn.addPerson(created);
            return created;
        });
        person.setFirstName(firstName);
        person.setLastName(lastName);
        person.setTaxId(taxId);
        person.setMaritalStatus(maritalStatus);
    }

    private PersonEntity resolvePerson(TaxReturnEntity taxReturn, PersonRole role) {
        return taxReturn.findPerson(role)
                .orElseThrow(() -> new IllegalStateException("Person for role %s is missing.".formatted(role)));
    }

    private boolean belongsToStep(ValidationIssueEntity issue, InterviewStep step) {
        String path = issue.getFieldPath();
        return switch (step) {
            case BASE_DATA -> path.startsWith("base.");
            case INCOME -> path.startsWith("income.");
            case WORK_EXPENSES -> path.startsWith("workexpenses.") || path.startsWith("workexpenses") || path.startsWith("deduction.items");
            case INSURANCE -> path.startsWith("insurance.");
            case SPECIAL_EXPENSES -> path.startsWith("specialexpenses.");
            case HOUSEHOLD_SERVICES -> path.startsWith("household.");
            case EXTRAORDINARY_BURDENS -> path.startsWith("extraordinary.") || path.startsWith("belastungen.");
            case REVIEW -> true;
        };
    }

    private List<PersonRole> relevantRoles(FilingMode filingMode) {
        return filingMode == FilingMode.JOINT
                ? List.of(PersonRole.TAXPAYER, PersonRole.SPOUSE)
                : List.of(PersonRole.TAXPAYER);
    }

    private PersonRole parseRole(String rawValue) {
        return rawValue != null && rawValue.equalsIgnoreCase(PersonRole.SPOUSE.name())
                ? PersonRole.SPOUSE
                : PersonRole.TAXPAYER;
    }

    private String readText(MultiValueMap<String, String> formData, String key) {
        String value = formData.getFirst(key);
        return value == null ? "" : value.trim();
    }

    private String valueAt(List<String> values, int index) {
        return index < values.size() ? values.get(index).trim() : "";
    }

    private BigDecimal parseAmount(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String normalized = rawValue.replace(".", "").replace(',', '.');
        return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean matchesRole(DeductionItemEntity item, PersonRole role) {
        return role == null
                ? item.getPerson() == null
                : item.getPerson() != null && item.getPerson().getRole() == role;
    }

    private String decimal(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<DeductionItemEntity> items(DeductionItemEntity... items) {
        return java.util.Arrays.stream(items)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public record IncomeRow(
            PersonRole role,
            String employerName,
            String grossWages,
            String wageTax,
            String solidaritySurcharge,
            String churchTax) {
    }

    public record StepStatus(InterviewStep step, String status) {
    }

    public enum DeductionFieldKey {
        TAXPAYER_COMMUTE(TaxCalculationService.CATEGORY_WORK_EXPENSES, "PENDLERPAUSCHALE", PersonRole.TAXPAYER),
        TAXPAYER_TRAVEL(TaxCalculationService.CATEGORY_WORK_EXPENSES, "REISEKOSTEN", PersonRole.TAXPAYER),
        TAXPAYER_EQUIPMENT(TaxCalculationService.CATEGORY_WORK_EXPENSES, "ARBEITSMITTEL", PersonRole.TAXPAYER),
        TAXPAYER_UNION_FEES(TaxCalculationService.CATEGORY_WORK_EXPENSES, "GEWERKSCHAFT", PersonRole.TAXPAYER),
        SPOUSE_COMMUTE(TaxCalculationService.CATEGORY_WORK_EXPENSES, "PENDLERPAUSCHALE", PersonRole.SPOUSE),
        SPOUSE_TRAVEL(TaxCalculationService.CATEGORY_WORK_EXPENSES, "REISEKOSTEN", PersonRole.SPOUSE),
        SPOUSE_EQUIPMENT(TaxCalculationService.CATEGORY_WORK_EXPENSES, "ARBEITSMITTEL", PersonRole.SPOUSE),
        SPOUSE_UNION_FEES(TaxCalculationService.CATEGORY_WORK_EXPENSES, "GEWERKSCHAFT", PersonRole.SPOUSE),
        TAXPAYER_PENSION(TaxCalculationService.CATEGORY_INSURANCE, "RENTENVERSICHERUNG", PersonRole.TAXPAYER),
        TAXPAYER_HEALTH(TaxCalculationService.CATEGORY_INSURANCE, "KRANKENVERSICHERUNG", PersonRole.TAXPAYER),
        TAXPAYER_UNEMPLOYMENT(TaxCalculationService.CATEGORY_INSURANCE, "ARBEITSLOSENVERSICHERUNG", PersonRole.TAXPAYER),
        TAXPAYER_LIABILITY(TaxCalculationService.CATEGORY_INSURANCE, "HAFTPFLICHT", PersonRole.TAXPAYER),
        SPOUSE_PENSION(TaxCalculationService.CATEGORY_INSURANCE, "RENTENVERSICHERUNG", PersonRole.SPOUSE),
        SPOUSE_HEALTH(TaxCalculationService.CATEGORY_INSURANCE, "KRANKENVERSICHERUNG", PersonRole.SPOUSE),
        SPOUSE_UNEMPLOYMENT(TaxCalculationService.CATEGORY_INSURANCE, "ARBEITSLOSENVERSICHERUNG", PersonRole.SPOUSE),
        SPOUSE_LIABILITY(TaxCalculationService.CATEGORY_INSURANCE, "HAFTPFLICHT", PersonRole.SPOUSE),
        TAXPAYER_DONATIONS(TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "SPENDEN", PersonRole.TAXPAYER),
        TAXPAYER_CHURCH_TAX(TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "KIRCHENSTEUER", PersonRole.TAXPAYER),
        TAXPAYER_EDUCATION(TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "WEITERBILDUNG", PersonRole.TAXPAYER),
        SPOUSE_DONATIONS(TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "SPENDEN", PersonRole.SPOUSE),
        SPOUSE_CHURCH_TAX(TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "KIRCHENSTEUER", PersonRole.SPOUSE),
        SPOUSE_EDUCATION(TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "WEITERBILDUNG", PersonRole.SPOUSE),
        HOUSEHOLD_SERVICES(TaxCalculationService.CATEGORY_HOUSEHOLD, "HAUSHALTSDIENSTLEISTUNGEN", null),
        CRAFTSMEN_SERVICES(TaxCalculationService.CATEGORY_HOUSEHOLD, "HANDWERKERLEISTUNGEN", null),
        MEDICAL_COSTS(TaxCalculationService.CATEGORY_EXTRAORDINARY, "KRANKHEITSKOSTEN", null),
        CARE_COSTS(TaxCalculationService.CATEGORY_EXTRAORDINARY, "PFLEGEKOSTEN", null),
        OTHER_EXTRAORDINARY_COSTS(TaxCalculationService.CATEGORY_EXTRAORDINARY, "SONSTIGE", null);

        private final String category;
        private final String subcategory;
        private final PersonRole role;

        DeductionFieldKey(String category, String subcategory, PersonRole role) {
            this.category = category;
            this.subcategory = subcategory;
            this.role = role;
        }

        public String category() {
            return category;
        }

        public String subcategory() {
            return subcategory;
        }

        public PersonRole role() {
            return role;
        }
    }
}
