package de.johannesrabauer.steuererklaerung2025.domain.model;

import jakarta.persistence.Column;
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

    @Column(name = "employer_name", nullable = false, length = 200)
    private String employerName;

    @Column(name = "gross_wages", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossWages = BigDecimal.ZERO;

    @Column(name = "wage_tax", nullable = false, precision = 19, scale = 2)
    private BigDecimal wageTax = BigDecimal.ZERO;

    @Column(name = "solidarity_surcharge", nullable = false, precision = 19, scale = 2)
    private BigDecimal solidaritySurcharge = BigDecimal.ZERO;

    @Column(name = "church_tax", nullable = false, precision = 19, scale = 2)
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
