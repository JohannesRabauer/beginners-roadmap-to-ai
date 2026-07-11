package de.johannesrabauer.steuererklaerung2025.domain.model;

import de.johannesrabauer.steuererklaerung2025.infrastructure.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "export_bundles")
public class ExportBundleEntity extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tax_return_id", nullable = false, unique = true)
    private TaxReturnEntity taxReturn;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "pdf_path", columnDefinition = "clob")
    private String pdfPath;

    @Column(name = "elster_shape_version", nullable = false, length = 32)
    private String elsterShapeVersion;

    @Column(name = "export_status", nullable = false, length = 32)
    private String exportStatus = "DRAFT";

    public TaxReturnEntity getTaxReturn() {
        return taxReturn;
    }

    public void setTaxReturn(TaxReturnEntity taxReturn) {
        this.taxReturn = taxReturn;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public String getElsterShapeVersion() {
        return elsterShapeVersion;
    }

    public void setElsterShapeVersion(String elsterShapeVersion) {
        this.elsterShapeVersion = elsterShapeVersion;
    }

    public String getExportStatus() {
        return exportStatus;
    }

    public void setExportStatus(String exportStatus) {
        this.exportStatus = exportStatus;
    }
}
