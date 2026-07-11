package de.johannesrabauer.steuererklaerung2025.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.johannesrabauer.steuererklaerung2025.domain.model.ExportBundleEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonRole;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationIssueEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationSeverity;
import de.johannesrabauer.steuererklaerung2025.domain.model.WageTaxCertificateEntity;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TaxExportService {

    private static final float PAGE_MARGIN = 48f;
    private static final float LINE_HEIGHT = 16f;

    private final Path exportDirectory;
    private final ObjectMapper objectMapper;
    private final TaxCalculationService calculationService;

    public TaxExportService(
            @Value("${steuererklaerung.exports.directory}") String exportDirectory,
            ObjectMapper objectMapper,
            TaxCalculationService calculationService) {
        this.exportDirectory = Path.of(exportDirectory);
        this.objectMapper = objectMapper;
        this.calculationService = calculationService;
    }

    public Path generatePdfSummary(TaxReturnEntity taxReturn, List<ValidationIssueEntity> validationIssues) {
        TaxCalculationService.ReviewSummary reviewSummary = calculationService.buildReviewSummary(taxReturn, validationIssues);
        Path pdfPath = exportDirectory.resolve(filePrefix(taxReturn) + "-zusammenfassung.pdf");
        ensureDirectoryExists();

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font boldFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            float y = page.getMediaBox().getHeight() - PAGE_MARGIN;

            y = writeLine(contentStream, boldFont, 16, y, "Steuererklaerung 2025 - Lokale Zusammenfassung");
            y = writeLine(contentStream, font, 10, y, "Erstellt am: " + Instant.now());
            y = writeLine(contentStream, font, 10, y, "Hinweis: Diese Anwendung unterstuetzt die Vorbereitung, ersetzt aber keine amtliche Abgabe oder Beratung.");
            y -= 8;

            for (TaxCalculationService.ReviewSection section : reviewSummary.sections()) {
                if (y < PAGE_MARGIN + 120) {
                    contentStream.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - PAGE_MARGIN;
                }
                y = writeLine(contentStream, boldFont, 12, y, section.title());
                for (String line : section.lines()) {
                    y = writeLine(contentStream, font, 10, y, "- " + line);
                }
                y -= 6;
            }

            y = writeLine(contentStream, boldFont, 12, y, "Regelspur");
            for (TaxCalculationService.RuleTrace trace : reviewSummary.calculation().traces()) {
                y = writeLine(contentStream, font, 10, y, trace.ruleCode() + ": " + trace.description());
                y = writeLine(contentStream, font, 10, y, "  Ergebnis: " + money(trace.result()) + " EUR");
            }
            contentStream.close();
            document.save(pdfPath.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Die PDF-Zusammenfassung konnte nicht erstellt werden.", exception);
        }

        ExportBundleEntity exportBundle = ensureExportBundle(taxReturn);
        exportBundle.setPdfPath(pdfPath.toString());
        exportBundle.setExportStatus("EXPORTED");
        return pdfPath;
    }

    public Path generateElsterShapeExport(TaxReturnEntity taxReturn, List<ValidationIssueEntity> validationIssues) {
        TaxCalculationService.ReviewSummary reviewSummary = calculationService.buildReviewSummary(taxReturn, validationIssues);
        Path exportPath = exportDirectory.resolve(filePrefix(taxReturn) + "-elster-shape.json");
        ensureDirectoryExists();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", "2025-v1");
        payload.put("generatedAt", Instant.now().toString());
        payload.put("officialSubmissionIncluded", false);
        payload.put("filingMode", taxReturn.getFilingMode().name());
        payload.put("taxYear", taxReturn.getTaxYear());
        payload.put("baseData", baseData(taxReturn));
        payload.put("persons", people(taxReturn));
        payload.put("incomeRecords", incomeRecords(taxReturn));
        payload.put("deductions", deductionRecords(taxReturn));
        payload.put("reviewSummary", reviewSummary.sections());
        payload.put("calculations", reviewSummary.calculation());
        payload.put("validationIssues", validationIssues.stream().map(issue -> Map.of(
                "severity", issue.getSeverity().name(),
                "fieldPath", issue.getFieldPath(),
                "message", issue.getMessage())).toList());

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(exportPath.toFile(), payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Der strukturierte Export konnte nicht erstellt werden.", exception);
        }

        ExportBundleEntity exportBundle = ensureExportBundle(taxReturn);
        exportBundle.setElsterExportPath(exportPath.toString());
        exportBundle.setExportStatus("EXPORTED");
        return exportPath;
    }

    private Map<String, Object> baseData(TaxReturnEntity taxReturn) {
        Map<String, Object> baseData = new LinkedHashMap<>();
        baseData.put("streetAddress", taxReturn.getStreetAddress());
        baseData.put("postalCode", taxReturn.getPostalCode());
        baseData.put("city", taxReturn.getCity());
        baseData.put("taxOffice", taxReturn.getTaxOffice());
        baseData.put("iban", taxReturn.getIban());
        baseData.put("bankAccountHolder", taxReturn.getBankAccountHolder());
        return baseData;
    }

    private List<Map<String, Object>> people(TaxReturnEntity taxReturn) {
        return taxReturn.getPersons().stream()
                .map(person -> {
                    Map<String, Object> personPayload = new LinkedHashMap<>();
                    personPayload.put("role", person.getRole().name());
                    personPayload.put("firstName", person.getFirstName());
                    personPayload.put("lastName", person.getLastName());
                    personPayload.put("taxId", person.getTaxId());
                    personPayload.put("maritalStatus", person.getMaritalStatus());
                    return personPayload;
                })
                .toList();
    }

    private List<Map<String, Object>> incomeRecords(TaxReturnEntity taxReturn) {
        return taxReturn.getWageTaxCertificates().stream()
                .map(certificate -> {
                    Map<String, Object> incomePayload = new LinkedHashMap<>();
                    incomePayload.put("personRole", certificate.getPerson() == null ? PersonRole.TAXPAYER.name() : certificate.getPerson().getRole().name());
                    incomePayload.put("employerName", certificate.getEmployerName());
                    incomePayload.put("grossWages", certificate.getGrossWages());
                    incomePayload.put("wageTax", certificate.getWageTax());
                    incomePayload.put("solidaritySurcharge", certificate.getSolidaritySurcharge());
                    incomePayload.put("churchTax", certificate.getChurchTax());
                    return incomePayload;
                })
                .toList();
    }

    private List<Map<String, Object>> deductionRecords(TaxReturnEntity taxReturn) {
        return taxReturn.getDeductionItems().stream()
                .map(item -> {
                    Map<String, Object> deductionPayload = new LinkedHashMap<>();
                    deductionPayload.put("category", item.getCategory());
                    deductionPayload.put("subcategory", item.getSubcategory());
                    deductionPayload.put("personRole", item.getPerson() == null ? "JOINT" : item.getPerson().getRole().name());
                    deductionPayload.put("amount", item.getAmount());
                    deductionPayload.put("evidenceStatus", item.getEvidenceStatus());
                    return deductionPayload;
                })
                .toList();
    }

    private ExportBundleEntity ensureExportBundle(TaxReturnEntity taxReturn) {
        if (taxReturn.getExportBundle() == null) {
            ExportBundleEntity exportBundle = new ExportBundleEntity();
            exportBundle.setElsterShapeVersion("2025-v1");
            exportBundle.setExportStatus("READY");
            taxReturn.setExportBundle(exportBundle);
        }
        return taxReturn.getExportBundle();
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(exportDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Das Exportverzeichnis konnte nicht angelegt werden.", exception);
        }
    }

    private String filePrefix(TaxReturnEntity taxReturn) {
        return "%d-%s".formatted(taxReturn.getTaxYear(), taxReturn.getId());
    }

    private float writeLine(PDPageContentStream contentStream, PDType1Font font, int fontSize, float y, String text) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(PAGE_MARGIN, y);
        contentStream.showText(text);
        contentStream.endText();
        return y - LINE_HEIGHT;
    }

    private String money(BigDecimal value) {
        return value == null ? "0,00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
