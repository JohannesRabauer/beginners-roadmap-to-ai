package de.johannesrabauer.steuererklaerung2025.domain.model;

import jakarta.persistence.Column;
import de.johannesrabauer.steuererklaerung2025.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "tax_returns")
public class TaxReturnEntity extends AuditableEntity {

    @Column(name = "tax_year", nullable = false)
    private int taxYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "filing_mode", nullable = false, length = 16)
    private FilingMode filingMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReturnStatus status = ReturnStatus.DRAFT;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "street_address", columnDefinition = "clob")
    private String streetAddress;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "postal_code", columnDefinition = "clob")
    private String postalCode;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "city", columnDefinition = "clob")
    private String city;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "iban", columnDefinition = "clob")
    private String iban;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bank_account_holder", columnDefinition = "clob")
    private String bankAccountHolder;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "tax_office", columnDefinition = "clob")
    private String taxOffice;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_interview_step", nullable = false, length = 32)
    private InterviewStep currentInterviewStep = InterviewStep.BASE_DATA;

    @OneToMany(mappedBy = "taxReturn", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private final List<PersonEntity> persons = new ArrayList<>();

    @OneToMany(mappedBy = "taxReturn", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private final List<WageTaxCertificateEntity> wageTaxCertificates = new ArrayList<>();

    @OneToMany(mappedBy = "taxReturn", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private final List<DeductionItemEntity> deductionItems = new ArrayList<>();

    @OneToMany(mappedBy = "taxReturn", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private final List<ValidationIssueEntity> validationIssues = new ArrayList<>();

    @OneToOne(fetch = jakarta.persistence.FetchType.LAZY, mappedBy = "taxReturn", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    private ExportBundleEntity exportBundle;

    public int getTaxYear() {
        return taxYear;
    }

    public void setTaxYear(int taxYear) {
        this.taxYear = taxYear;
    }

    public FilingMode getFilingMode() {
        return filingMode;
    }

    public void setFilingMode(FilingMode filingMode) {
        this.filingMode = filingMode;
    }

    public ReturnStatus getStatus() {
        return status;
    }

    public void setStatus(ReturnStatus status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBankAccountHolder() {
        return bankAccountHolder;
    }

    public void setBankAccountHolder(String bankAccountHolder) {
        this.bankAccountHolder = bankAccountHolder;
    }

    public String getTaxOffice() {
        return taxOffice;
    }

    public void setTaxOffice(String taxOffice) {
        this.taxOffice = taxOffice;
    }

    public InterviewStep getCurrentInterviewStep() {
        return currentInterviewStep;
    }

    public void setCurrentInterviewStep(InterviewStep currentInterviewStep) {
        this.currentInterviewStep = currentInterviewStep;
    }

    public List<PersonEntity> getPersons() {
        return persons;
    }

    public List<WageTaxCertificateEntity> getWageTaxCertificates() {
        return wageTaxCertificates;
    }

    public List<DeductionItemEntity> getDeductionItems() {
        return deductionItems;
    }

    public List<ValidationIssueEntity> getValidationIssues() {
        return validationIssues;
    }

    public ExportBundleEntity getExportBundle() {
        return exportBundle;
    }

    public void setExportBundle(ExportBundleEntity exportBundle) {
        this.exportBundle = exportBundle;
        if (exportBundle != null) {
            exportBundle.setTaxReturn(this);
        }
    }

    public void addPerson(PersonEntity person) {
        this.persons.add(person);
        person.setTaxReturn(this);
    }

    public void replacePersons(List<PersonEntity> persons) {
        this.persons.clear();
        persons.forEach(this::addPerson);
    }

    public Optional<PersonEntity> findPerson(PersonRole role) {
        return persons.stream()
                .filter(person -> person.getRole() == role)
                .findFirst();
    }

    public void addWageTaxCertificate(WageTaxCertificateEntity certificate) {
        this.wageTaxCertificates.add(certificate);
        certificate.setTaxReturn(this);
    }

    public void replaceWageTaxCertificates(List<WageTaxCertificateEntity> wageTaxCertificates) {
        this.wageTaxCertificates.clear();
        wageTaxCertificates.forEach(this::addWageTaxCertificate);
    }

    public void addDeductionItem(DeductionItemEntity deductionItem) {
        this.deductionItems.add(deductionItem);
        deductionItem.setTaxReturn(this);
    }

    public void replaceDeductionItems(List<DeductionItemEntity> deductionItems) {
        this.deductionItems.clear();
        deductionItems.forEach(this::addDeductionItem);
    }

    public void addValidationIssue(ValidationIssueEntity validationIssue) {
        this.validationIssues.add(validationIssue);
        validationIssue.setTaxReturn(this);
    }

    public void replaceValidationIssues(List<ValidationIssueEntity> validationIssues) {
        this.validationIssues.clear();
        validationIssues.forEach(this::addValidationIssue);
    }
}
