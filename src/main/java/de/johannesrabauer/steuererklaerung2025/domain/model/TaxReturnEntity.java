package de.johannesrabauer.steuererklaerung2025.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    public void addWageTaxCertificate(WageTaxCertificateEntity certificate) {
        this.wageTaxCertificates.add(certificate);
        certificate.setTaxReturn(this);
    }

    public void addDeductionItem(DeductionItemEntity deductionItem) {
        this.deductionItems.add(deductionItem);
        deductionItem.setTaxReturn(this);
    }

    public void addValidationIssue(ValidationIssueEntity validationIssue) {
        this.validationIssues.add(validationIssue);
        validationIssue.setTaxReturn(this);
    }
}
