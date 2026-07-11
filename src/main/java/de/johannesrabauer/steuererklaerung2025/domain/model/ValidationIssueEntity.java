package de.johannesrabauer.steuererklaerung2025.domain.model;

import de.johannesrabauer.steuererklaerung2025.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "validation_issues")
public class ValidationIssueEntity extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false)
    private TaxReturnEntity taxReturn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ValidationSeverity severity;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "field_path", nullable = false, columnDefinition = "clob")
    private String fieldPath;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(nullable = false, columnDefinition = "clob")
    private String message;

    public TaxReturnEntity getTaxReturn() {
        return taxReturn;
    }

    public void setTaxReturn(TaxReturnEntity taxReturn) {
        this.taxReturn = taxReturn;
    }

    public ValidationSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(ValidationSeverity severity) {
        this.severity = severity;
    }

    public String getFieldPath() {
        return fieldPath;
    }

    public void setFieldPath(String fieldPath) {
        this.fieldPath = fieldPath;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
