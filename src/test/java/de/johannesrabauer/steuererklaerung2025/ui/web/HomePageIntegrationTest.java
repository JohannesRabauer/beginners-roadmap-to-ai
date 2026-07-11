package de.johannesrabauer.steuererklaerung2025.ui.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import de.johannesrabauer.steuererklaerung2025.infrastructure.persistence.jpa.SpringDataTaxReturnRepository;
import de.johannesrabauer.steuererklaerung2025.infrastructure.security.PassphraseService;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomePageIntegrationTest {

    private static Path testDirectory;
    private static Path passphraseFile;
    private static Path exportDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PassphraseService passphraseService;

    @Autowired
    private SpringDataTaxReturnRepository taxReturnRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("steuererklaerung.security.passphrase-file", () -> passphraseFile.toString());
        registry.add("steuererklaerung.exports.directory", () -> exportDirectory.toString());
    }

    @BeforeAll
    static void createTestDirectory() throws IOException {
        testDirectory = Files.createTempDirectory("steuererklaerung-web-test-");
        passphraseFile = testDirectory.resolve("web-passphrase.properties");
        exportDirectory = testDirectory.resolve("exports");
    }

    @AfterAll
    static void deleteTestDirectory() throws IOException {
        Files.deleteIfExists(passphraseFile);
        if (Files.exists(exportDirectory)) {
            try (var paths = Files.list(exportDirectory)) {
                paths.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
            Files.deleteIfExists(exportDirectory);
        }
        Files.deleteIfExists(testDirectory);
    }

    @BeforeEach
    void resetPassphraseState() throws IOException {
        passphraseService.deletePassphraseFileForTests();
    }

    @AfterEach
    void clearTaxReturns() {
        taxReturnRepository.deleteAll();
    }

    @Test
    void redirects_to_setup_flow_until_a_passphrase_exists() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/passphrase/setup"));

        mockMvc.perform(get("/passphrase/setup"))
                .andExpect(status().isOk())
                .andExpect(view().name("passphrase-setup"))
                .andExpect(content().string(containsString("Passphrase")));
    }

    @Test
    void full_wizard_supports_review_export_completion_and_deletion() throws Exception {
        MvcResult setupResult = mockMvc.perform(post("/passphrase/setup")
                        .param("passphrase", "SehrSicherePassphrase123")
                        .param("passphraseConfirmation", "SehrSicherePassphrase123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        org.springframework.mock.web.MockHttpSession testSession =
                (org.springframework.mock.web.MockHttpSession) setupResult.getRequest().getSession(false);

        MvcResult createResult = mockMvc.perform(post("/returns")
                        .session(testSession)
                        .param("filingMode", "JOINT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/?opened=*"))
                .andReturn();

        String returnId = extractOpenedReturnId(createResult.getResponse().getRedirectedUrl());

        mockMvc.perform(post("/returns/" + returnId + "/wizard/basisdaten")
                        .session(testSession)
                        .param("taxpayerFirstName", "Anna")
                        .param("taxpayerLastName", "Muster")
                        .param("taxpayerTaxId", "12345678901")
                        .param("taxpayerMaritalStatus", "VERHEIRATET")
                        .param("spouseFirstName", "Ben")
                        .param("spouseLastName", "Muster")
                        .param("spouseTaxId", "10987654321")
                        .param("spouseMaritalStatus", "VERHEIRATET")
                        .param("streetAddress", "Musterweg 1")
                        .param("postalCode", "10115")
                        .param("city", "Berlin")
                        .param("taxOffice", "Finanzamt Mitte")
                        .param("bankAccountHolder", "Anna Muster")
                        .param("iban", "DE02120300000000202051")
                        .param("navigationAction", "next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/einkuenfte"));

        mockMvc.perform(post("/returns/" + returnId + "/wizard/einkuenfte")
                        .session(testSession)
                        .param("incomeRole", "TAXPAYER", "SPOUSE", "TAXPAYER", "SPOUSE")
                        .param("incomeEmployerName", "Muster GmbH", "Beispiel AG", "", "")
                        .param("incomeGrossWages", "50000", "32000", "0", "0")
                        .param("incomeWageTax", "8000", "4200", "0", "0")
                        .param("incomeSolidaritySurcharge", "0", "0", "0", "0")
                        .param("incomeChurchTax", "0", "0", "0", "0")
                        .param("navigationAction", "next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/werbungskosten"));

        mockMvc.perform(post("/returns/" + returnId + "/wizard/werbungskosten")
                        .session(testSession)
                        .param("taxpayerCommute", "1500")
                        .param("taxpayerCommuteEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerTravel", "250")
                        .param("taxpayerTravelEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerEquipment", "500")
                        .param("taxpayerEquipmentEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerUnionFees", "120")
                        .param("taxpayerUnionFeesEvidence", "BELEG_VORHANDEN")
                        .param("spouseCommute", "800")
                        .param("spouseCommuteEvidence", "BELEG_VORHANDEN")
                        .param("spouseTravel", "120")
                        .param("spouseTravelEvidence", "BELEG_VORHANDEN")
                        .param("spouseEquipment", "300")
                        .param("spouseEquipmentEvidence", "BELEG_VORHANDEN")
                        .param("spouseUnionFees", "50")
                        .param("spouseUnionFeesEvidence", "BELEG_VORHANDEN")
                        .param("navigationAction", "next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/vorsorge"));

        mockMvc.perform(post("/returns/" + returnId + "/wizard/vorsorge")
                        .session(testSession)
                        .param("taxpayerPension", "4000")
                        .param("taxpayerPensionEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerHealth", "2500")
                        .param("taxpayerHealthEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerUnemployment", "500")
                        .param("taxpayerUnemploymentEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerLiability", "180")
                        .param("taxpayerLiabilityEvidence", "BELEG_VORHANDEN")
                        .param("spousePension", "2200")
                        .param("spousePensionEvidence", "BELEG_VORHANDEN")
                        .param("spouseHealth", "1800")
                        .param("spouseHealthEvidence", "BELEG_VORHANDEN")
                        .param("spouseUnemployment", "250")
                        .param("spouseUnemploymentEvidence", "BELEG_VORHANDEN")
                        .param("spouseLiability", "120")
                        .param("spouseLiabilityEvidence", "BELEG_VORHANDEN")
                        .param("navigationAction", "next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/sonderausgaben"));

        mockMvc.perform(post("/returns/" + returnId + "/wizard/sonderausgaben")
                        .session(testSession)
                        .param("taxpayerDonations", "300")
                        .param("taxpayerDonationsEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerChurchTax", "150")
                        .param("taxpayerChurchTaxEvidence", "BELEG_VORHANDEN")
                        .param("taxpayerEducation", "0")
                        .param("taxpayerEducationEvidence", "")
                        .param("spouseDonations", "200")
                        .param("spouseDonationsEvidence", "BELEG_VORHANDEN")
                        .param("spouseChurchTax", "0")
                        .param("spouseChurchTaxEvidence", "")
                        .param("spouseEducation", "50")
                        .param("spouseEducationEvidence", "BELEG_VORHANDEN")
                        .param("navigationAction", "next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/haushalt"));

        mockMvc.perform(post("/returns/" + returnId + "/wizard/haushalt")
                        .session(testSession)
                        .param("householdServices", "1200")
                        .param("householdServicesEvidence", "BELEG_VORHANDEN")
                        .param("craftsmenServices", "800")
                        .param("craftsmenServicesEvidence", "BELEG_VORHANDEN")
                        .param("navigationAction", "next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/belastungen"));

        mockMvc.perform(post("/returns/" + returnId + "/wizard/belastungen")
                        .session(testSession)
                        .param("medicalCosts", "600")
                        .param("medicalCostsEvidence", "BELEG_VORHANDEN")
                        .param("careCosts", "0")
                        .param("careCostsEvidence", "")
                        .param("otherExtraordinaryCosts", "100")
                        .param("otherExtraordinaryCostsEvidence", "BELEG_VORHANDEN")
                        .param("navigationAction", "next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/pruefung"));

        mockMvc.perform(get("/returns/" + returnId + "/wizard/pruefung").session(testSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("exportbereit")))
                .andExpect(content().string(containsString("Steuerermaessigung Haushalt/Handwerk")));

        mockMvc.perform(post("/returns/" + returnId + "/exports/pdf").session(testSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/pruefung"));

        mockMvc.perform(post("/returns/" + returnId + "/exports/elster").session(testSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/pruefung"));

        mockMvc.perform(post("/returns/" + returnId + "/review/complete").session(testSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/returns/" + returnId + "/wizard/pruefung"));

        var savedReturn = taxReturnRepository.findById(java.util.UUID.fromString(returnId)).orElseThrow();
        assertThat(savedReturn.getStatus().name()).isEqualTo("COMPLETED");
        assertThat(savedReturn.getValidationIssues()).allMatch(issue -> issue.getSeverity() != de.johannesrabauer.steuererklaerung2025.domain.model.ValidationSeverity.ERROR);
        assertThat(savedReturn.getExportBundle().getPdfPath()).isNotBlank();
        assertThat(savedReturn.getExportBundle().getElsterExportPath()).isNotBlank();
        assertThat(Files.exists(Path.of(savedReturn.getExportBundle().getPdfPath()))).isTrue();
        assertThat(Files.exists(Path.of(savedReturn.getExportBundle().getElsterExportPath()))).isTrue();

        mockMvc.perform(post("/returns/" + returnId + "/delete").session(testSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        assertThat(taxReturnRepository.findById(java.util.UUID.fromString(returnId))).isEmpty();
    }

    private String extractOpenedReturnId(String redirectedUrl) {
        if (redirectedUrl == null) {
            throw new IllegalStateException("Redirect URL is missing.");
        }
        String query = URI.create("http://localhost" + redirectedUrl).getQuery();
        if (query == null || !query.startsWith("opened=")) {
            throw new IllegalStateException("opened query parameter is missing.");
        }
        return query.substring("opened=".length());
    }
}
