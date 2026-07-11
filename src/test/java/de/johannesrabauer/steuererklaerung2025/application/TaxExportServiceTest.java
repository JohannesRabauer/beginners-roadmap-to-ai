package de.johannesrabauer.steuererklaerung2025.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.johannesrabauer.steuererklaerung2025.support.TaxReturnFixtureFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaxExportServiceTest {

    @TempDir
    Path tempDir;

    private final TaxCalculationService calculationService = new TaxCalculationService();
    private final TaxValidationService validationService = new TaxValidationService();

    @Test
    void creates_local_pdf_and_json_exports() throws IOException {
        var taxReturn = TaxReturnFixtureFactory.completeJointReturn();
        var issues = validationService.validate(taxReturn);
        var exportService = new TaxExportService(tempDir.toString(), new ObjectMapper(), calculationService);

        Path pdfPath = exportService.generatePdfSummary(taxReturn, issues);
        Path jsonPath = exportService.generateElsterShapeExport(taxReturn, issues);

        assertThat(Files.exists(pdfPath)).isTrue();
        assertThat(Files.size(pdfPath)).isGreaterThan(0);
        assertThat(Files.exists(jsonPath)).isTrue();
        assertThat(Files.readString(jsonPath)).contains("\"schemaVersion\" : \"2025-v1\"");
        assertThat(taxReturn.getExportBundle().getPdfPath()).isEqualTo(pdfPath.toString());
        assertThat(taxReturn.getExportBundle().getElsterExportPath()).isEqualTo(jsonPath.toString());
    }
}
