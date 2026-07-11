package de.johannesrabauer.steuererklaerung2025.ui.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesRegex;
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
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PassphraseService passphraseService;

    @Autowired
    private SpringDataTaxReturnRepository taxReturnRepository;

    @Value("${steuererklaerung.ui-name}")
    private String applicationName;

    @Value("${steuererklaerung.tax-year}")
    private int taxYear;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("steuererklaerung.security.passphrase-file", () -> passphraseFile.toString());
    }

    @BeforeAll
    static void createTestDirectory() throws IOException {
        testDirectory = Files.createTempDirectory("steuererklaerung-web-test-");
        passphraseFile = testDirectory.resolve("web-passphrase.properties");
    }

    @AfterAll
    static void deleteTestDirectory() throws IOException {
        Files.deleteIfExists(passphraseFile);
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
    void redirects_to_the_setup_flow_until_a_passphrase_exists() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/passphrase/setup"));

        mockMvc.perform(get("/passphrase/setup"))
                .andExpect(status().isOk())
                .andExpect(view().name("passphrase-setup"))
                .andExpect(content().string(containsString("Passphrase für die lokale Anwendung einrichten")))
                .andExpect(content().string(containsString("nicht wiederhergestellt werden")));
    }

    @Test
    void unlock_flow_protects_the_home_page() throws Exception {
        MvcResult setupResult = mockMvc.perform(post("/passphrase/setup")
                        .param("passphrase", "SehrSicherePassphrase123")
                        .param("passphraseConfirmation", "SehrSicherePassphrase123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        mockMvc.perform(get("/").session((org.springframework.mock.web.MockHttpSession) setupResult.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString(applicationName)))
                .andExpect(content().string(containsString(">" + taxYear + "<")))
                .andExpect(content().string(containsString("id=\"lock-button\"")));

        mockMvc.perform(post("/lock").session((org.springframework.mock.web.MockHttpSession) setupResult.getRequest().getSession(false)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/unlock"));

        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/unlock"));

        mockMvc.perform(post("/unlock").param("passphrase", "falsch"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/unlock"));

        MvcResult unlockResult = mockMvc.perform(post("/unlock").param("passphrase", "SehrSicherePassphrase123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        mockMvc.perform(get("/").session((org.springframework.mock.web.MockHttpSession) unlockResult.getRequest().getSession(false)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Die Anwendung ist entsperrt.")));
    }

    @Test
    void dashboard_supports_create_open_complete_and_resume() throws Exception {
        MvcResult setupResult = mockMvc.perform(post("/passphrase/setup")
                        .param("passphrase", "SehrSicherePassphrase123")
                        .param("passphraseConfirmation", "SehrSicherePassphrase123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        org.springframework.mock.web.MockHttpSession testSession =
                (org.springframework.mock.web.MockHttpSession) setupResult.getRequest().getSession(false);

        MvcResult createResult = mockMvc.perform(post("/returns")
                        .session(testSession)
                        .param("filingMode", "JOINT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/?opened=*"))
                .andReturn();

        String redirectUrl = createResult.getResponse().getRedirectedUrl();
        String returnId = redirectUrl.substring(redirectUrl.indexOf("opened=") + 7);

        mockMvc.perform(get("/").session(testSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Gemeinsame Veranlagung")))
                .andExpect(content().string(containsString("Entwurf")));

        mockMvc.perform(get("/returns/" + returnId).session(testSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?opened=" + returnId));

        mockMvc.perform(post("/returns/" + returnId + "/complete").session(testSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?opened=" + returnId));

        mockMvc.perform(get("/?opened=" + returnId).session(testSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Abgeschlossen")));

        mockMvc.perform(post("/returns/" + returnId + "/resume").session(testSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?opened=" + returnId));

        mockMvc.perform(get("/?opened=" + returnId).session(testSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Entwurf")))
                .andExpect(content().string(matchesRegex("(?s).*id=\\\"opened-return-id\\\">\\Q" + returnId + "\\E<.*")));
    }
}
