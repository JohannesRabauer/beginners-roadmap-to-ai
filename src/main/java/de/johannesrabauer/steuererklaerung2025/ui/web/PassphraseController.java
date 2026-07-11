package de.johannesrabauer.steuererklaerung2025.ui.web;

import de.johannesrabauer.steuererklaerung2025.infrastructure.security.PassphraseService;
import de.johannesrabauer.steuererklaerung2025.infrastructure.security.PassphraseSession;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PassphraseController {

    private final PassphraseService passphraseService;

    public PassphraseController(PassphraseService passphraseService) {
        this.passphraseService = passphraseService;
    }

    @GetMapping("/passphrase/setup")
    public String setupPage(Model model) {
        model.addAttribute("minimumPassphraseLength", passphraseService.getMinimumPassphraseLength());
        return "passphrase-setup";
    }

    @PostMapping("/passphrase/setup")
    public String setup(
            @RequestParam("passphrase") String passphrase,
            @RequestParam("passphraseConfirmation") String passphraseConfirmation,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        if (!passphrase.equals(passphraseConfirmation)) {
            redirectAttributes.addFlashAttribute("error", "Die Passphrasen stimmen nicht überein.");
            return "redirect:/passphrase/setup";
        }

        try {
            passphraseService.initialize(passphrase);
            session.setAttribute(PassphraseSession.UNLOCKED_ATTRIBUTE, true);
            redirectAttributes.addFlashAttribute("message", "Die Anwendung wurde geschützt und entsperrt.");
            return "redirect:/";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", "Die Passphrase konnte nicht eingerichtet werden: " + exception.getMessage());
            return "redirect:/passphrase/setup";
        }
    }

    @GetMapping("/unlock")
    public String unlockPage() {
        return "unlock";
    }

    @PostMapping("/unlock")
    public String unlock(
            @RequestParam("passphrase") String passphrase,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        try {
            if (!passphraseService.unlock(passphrase)) {
                redirectAttributes.addFlashAttribute("error", "Die Passphrase ist ungültig.");
                return "redirect:/unlock";
            }
            session.setAttribute(PassphraseSession.UNLOCKED_ATTRIBUTE, true);
            redirectAttributes.addFlashAttribute("message", "Die Anwendung ist jetzt entsperrt.");
            return "redirect:/";
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", "Die Anwendung konnte nicht entsperrt werden: " + exception.getMessage());
            return "redirect:/unlock";
        }
    }

    @PostMapping("/lock")
    public String lock(HttpSession session, RedirectAttributes redirectAttributes) {
        passphraseService.lock();
        session.invalidate();
        redirectAttributes.addFlashAttribute("message", "Die Anwendung wurde gesperrt.");
        return "redirect:/unlock";
    }
}
