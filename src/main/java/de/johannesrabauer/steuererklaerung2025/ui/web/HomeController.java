package de.johannesrabauer.steuererklaerung2025.ui.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("applicationName", "Steuererklaerung 2025 Assistant");
        model.addAttribute("taxYear", 2025);
        return "home";
    }
}
