package de.johannesrabauer.steuererklaerung2025.domain.model;

import de.johannesrabauer.steuererklaerung2025.infrastructure.security.EncryptedBigDecimalConverter;
import de.johannesrabauer.steuererklaerung2025.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "deduction_items")
public class DeductionItemEntity extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false)
    private TaxReturnEntity taxReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private PersonEntity person;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "clob")
    private String category;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(columnDefinition = "clob")
    private String subcategory;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(nullable = false, columnDefinition = "clob")
    private BigDecimal amount = BigDecimal.ZERO;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "evidence_status", nullable = false, columnDefinition = "clob")
    private String evidenceStatus = "NOT_PROVIDED";

    public TaxReturnEntity getTaxReturn() {
        return taxReturn;
    }

    public void setTaxReturn(TaxReturnEntity taxReturn) {
        this.taxReturn = taxReturn;
    }

    public PersonEntity getPerson() {
        return person;
    }

    public void setPerson(PersonEntity person) {
        this.person = person;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getEvidenceStatus() {
        return evidenceStatus;
    }

    public void setEvidenceStatus(String evidenceStatus) {
        this.evidenceStatus = evidenceStatus;
    }
}
