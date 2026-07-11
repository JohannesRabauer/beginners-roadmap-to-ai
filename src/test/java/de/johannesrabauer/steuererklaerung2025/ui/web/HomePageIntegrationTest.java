package de.johannesrabauer.steuererklaerung2025.ui.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomePageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${steuererklaerung.ui-name}")
    private String applicationName;

    @Value("${steuererklaerung.tax-year}")
    private int taxYear;

    @Test
    void renders_the_initial_shell_page() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString(applicationName)))
                .andExpect(content().string(containsString(">" + taxYear + "<")))
                .andExpect(content().string(containsString("id=\"disclaimer\"")))
                .andExpect(content().string(containsString("Steuerfall für " + taxYear + " anlegen")));
    }
}
