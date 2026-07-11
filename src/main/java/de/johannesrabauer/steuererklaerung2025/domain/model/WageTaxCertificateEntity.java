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
@Table(name = "wage_tax_certificates")
public class WageTaxCertificateEntity extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false)
    private TaxReturnEntity taxReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private PersonEntity person;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "employer_name", nullable = false, columnDefinition = "clob")
    private String employerName;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "gross_wages", nullable = false, columnDefinition = "clob")
    private BigDecimal grossWages = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "wage_tax", nullable = false, columnDefinition = "clob")
    private BigDecimal wageTax = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "solidarity_surcharge", nullable = false, columnDefinition = "clob")
    private BigDecimal solidaritySurcharge = BigDecimal.ZERO;

    @Convert(converter = EncryptedBigDecimalConverter.class)
    @Column(name = "church_tax", nullable = false, columnDefinition = "clob")
    private BigDecimal churchTax = BigDecimal.ZERO;

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

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public BigDecimal getGrossWages() {
        return grossWages;
    }

    public void setGrossWages(BigDecimal grossWages) {
        this.grossWages = grossWages;
    }

    public BigDecimal getWageTax() {
        return wageTax;
    }

    public void setWageTax(BigDecimal wageTax) {
        this.wageTax = wageTax;
    }

    public BigDecimal getSolidaritySurcharge() {
        return solidaritySurcharge;
    }

    public void setSolidaritySurcharge(BigDecimal solidaritySurcharge) {
        this.solidaritySurcharge = solidaritySurcharge;
    }

    public BigDecimal getChurchTax() {
        return churchTax;
    }

    public void setChurchTax(BigDecimal churchTax) {
        this.churchTax = churchTax;
    }
}
