package de.johannesrabauer.steuererklaerung2025.ui.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final String applicationName;
    private final int taxYear;

    public HomeController(
            @Value("${steuererklaerung.ui-name}") String applicationName,
            @Value("${steuererklaerung.tax-year}") int taxYear) {
        this.applicationName = applicationName;
        this.taxYear = taxYear;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("applicationName", applicationName);
        model.addAttribute("taxYear", taxYear);
        return "home";
    }
}
