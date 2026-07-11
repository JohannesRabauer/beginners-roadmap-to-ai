package de.johannesrabauer.steuererklaerung2025.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import de.johannesrabauer.steuererklaerung2025.domain.model.DeductionItemEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ExportBundleEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.FilingMode;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonRole;
import de.johannesrabauer.steuererklaerung2025.domain.model.ReturnStatus;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationIssueEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.WageTaxCertificateEntity;
import de.johannesrabauer.steuererklaerung2025.domain.port.TaxReturnRepository;
import de.johannesrabauer.steuererklaerung2025.infrastructure.security.PassphraseService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class TaxReturnPersistenceIntegrationTest {

    private static final Path TEST_DIRECTORY = createTempDirectory();
    private static final Path PASSPHRASE_FILE = TEST_DIRECTORY.resolve("persistence-passphrase.properties");

    @Autowired
    private TaxReturnRepository taxReturnRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PassphraseService passphraseService;

    @PersistenceContext
    private EntityManager entityManager;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("steuererklaerung.security.passphrase-file", () -> PASSPHRASE_FILE.toString());
    }

    @BeforeEach
    void initializePassphrase() throws IOException {
        passphraseService.deletePassphraseFileForTests();
        passphraseService.initialize("SehrSicherePassphrase123");
    }

    @Test
    void creates_the_expected_schema_tables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'PUBLIC'",
                String.class);

        assertThat(tables).contains(
                "TAX_RETURNS",
                "PERSONS",
                "WAGE_TAX_CERTIFICATES",
                "DEDUCTION_ITEMS",
                "VALIDATION_ISSUES",
                "EXPORT_BUNDLES");
    }

    @Test
    void persists_a_joint_return_aggregate_without_duplicate_return_rows() {
        TaxReturnEntity taxReturn = new TaxReturnEntity();
        taxReturn.setTaxYear(2025);
        taxReturn.setFilingMode(FilingMode.JOINT);
        taxReturn.setStatus(ReturnStatus.DRAFT);

        PersonEntity taxpayer = new PersonEntity();
        taxpayer.setRole(PersonRole.TAXPAYER);
        taxpayer.setFirstName("Anna");
        taxpayer.setLastName("Muster");
        taxpayer.setTaxId("12345678901");
        taxpayer.setMaritalStatus("VERHEIRATET");
        taxReturn.addPerson(taxpayer);

        PersonEntity spouse = new PersonEntity();
        spouse.setRole(PersonRole.SPOUSE);
        spouse.setFirstName("Ben");
        spouse.setLastName("Muster");
        spouse.setTaxId("10987654321");
        spouse.setMaritalStatus("VERHEIRATET");
        taxReturn.addPerson(spouse);

        WageTaxCertificateEntity certificate = new WageTaxCertificateEntity();
        certificate.setPerson(taxpayer);
        certificate.setEmployerName("Muster GmbH");
        certificate.setGrossWages(new BigDecimal("50000.00"));
        certificate.setWageTax(new BigDecimal("8000.00"));
        certificate.setSolidaritySurcharge(new BigDecimal("440.00"));
        certificate.setChurchTax(new BigDecimal("0.00"));
        taxReturn.addWageTaxCertificate(certificate);

        DeductionItemEntity deduction = new DeductionItemEntity();
        deduction.setPerson(spouse);
        deduction.setCategory("WERBUNGSKOSTEN");
        deduction.setSubcategory("FAHRTEN");
        deduction.setAmount(new BigDecimal("120.00"));
        deduction.setEvidenceStatus("AVAILABLE");
        taxReturn.addDeductionItem(deduction);

        ValidationIssueEntity issue = new ValidationIssueEntity();
        issue.setSeverity("WARNING");
        issue.setFieldPath("persons[1].taxId");
        issue.setMessage("Tax ID should be checked before export.");
        taxReturn.addValidationIssue(issue);

        ExportBundleEntity exportBundle = new ExportBundleEntity();
        exportBundle.setPdfPath("exports/2025-summary.pdf");
        exportBundle.setElsterShapeVersion("2025-01");
        exportBundle.setExportStatus("READY");
        taxReturn.setExportBundle(exportBundle);

        TaxReturnEntity saved = taxReturnRepository.save(taxReturn);
        entityManager.flush();
        entityManager.clear();

        String encryptedFirstName = jdbcTemplate.queryForObject("select cast(first_name as varchar) from persons where role = 'TAXPAYER'", String.class);
        String encryptedGrossWages = jdbcTemplate.queryForObject("select cast(gross_wages as varchar) from wage_tax_certificates", String.class);

        TaxReturnEntity loaded = taxReturnRepository.findById(saved.getId()).orElseThrow();

        assertThat(encryptedFirstName).startsWith("enc:v1:").doesNotContain("Anna");
        assertThat(encryptedGrossWages).startsWith("enc:v1:").doesNotContain("50000.00");
        assertThat(jdbcTemplate.queryForObject("select count(*) from tax_returns", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from persons", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from wage_tax_certificates", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from deduction_items", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from validation_issues", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("select count(*) from export_bundles", Long.class)).isEqualTo(1L);

        assertThat(loaded.getTaxYear()).isEqualTo(2025);
        assertThat(loaded.getFilingMode()).isEqualTo(FilingMode.JOINT);
        assertThat(loaded.getStatus()).isEqualTo(ReturnStatus.DRAFT);
        assertThat(loaded.getPersons()).extracting(PersonEntity::getRole).containsExactlyInAnyOrder(PersonRole.TAXPAYER, PersonRole.SPOUSE);
        assertThat(loaded.getPersons()).extracting(PersonEntity::getFirstName).containsExactlyInAnyOrder("Anna", "Ben");
        assertThat(loaded.getWageTaxCertificates()).hasSize(1);
        assertThat(loaded.getWageTaxCertificates().getFirst().getGrossWages()).isEqualByComparingTo("50000.00");
        assertThat(loaded.getDeductionItems()).hasSize(1);
        assertThat(loaded.getValidationIssues()).hasSize(1);
        assertThat(loaded.getExportBundle().getPdfPath()).isEqualTo("exports/2025-summary.pdf");
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("steuererklaerung-persistence-test-");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create a temporary persistence test directory.", exception);
        }
    }
}
