package de.johannesrabauer.steuererklaerung2025.ui.web;

import de.johannesrabauer.steuererklaerung2025.domain.model.FilingMode;
import de.johannesrabauer.steuererklaerung2025.domain.model.ReturnStatus;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.port.TaxReturnRepository;
import java.util.Arrays;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    private final TaxReturnRepository taxReturnRepository;
    private final String applicationName;
    private final int taxYear;

    public HomeController(
            TaxReturnRepository taxReturnRepository,
            @Value("${steuererklaerung.ui-name}") String applicationName,
            @Value("${steuererklaerung.tax-year}") int taxYear) {
        this.taxReturnRepository = taxReturnRepository;
        this.applicationName = applicationName;
        this.taxYear = taxYear;
    }

    @GetMapping("/")
    public String index(@RequestParam(name = "opened", required = false) UUID openedReturnId, Model model) {
        Optional<TaxReturnEntity> openedReturn = Optional.empty();
        if (openedReturnId != null) {
            openedReturn = taxReturnRepository.findById(openedReturnId)
                    .filter(taxReturn -> taxReturn.getTaxYear() == taxYear);
        }

        model.addAttribute("applicationName", applicationName);
        model.addAttribute("taxYear", taxYear);
        model.addAttribute("filingModes", FilingMode.values());
        model.addAttribute("returns", taxReturnRepository.findAllByTaxYear(taxYear));
        model.addAttribute("openedReturn", openedReturn.orElse(null));
        return "home";
    }

    @PostMapping("/returns")
    public String createReturn(
            @RequestParam("filingMode") String filingModeInput,
            RedirectAttributes redirectAttributes) {
        String normalizedFilingModeInput = filingModeInput == null ? "" : filingModeInput.trim();
        Optional<FilingMode> filingMode = Arrays.stream(FilingMode.values())
                .filter(mode -> mode.name().equalsIgnoreCase(normalizedFilingModeInput))
                .findFirst();

        if (filingMode.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Ungültiger Veranlagungstyp.");
            return "redirect:/";
        }

        TaxReturnEntity saved = taxReturnRepository.save(createDraftTaxReturn(filingMode.get()));
        redirectAttributes.addFlashAttribute("message", "Steuerfall wurde angelegt.");
        return "redirect:/?opened=" + saved.getId();
    }

    @GetMapping("/returns/{returnId}")
    public String openReturn(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        Optional<TaxReturnEntity> taxReturn = findReturnForCurrentYear(returnId);
        if (taxReturn.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Steuerfall wurde nicht gefunden.");
            return "redirect:/";
        }
        return "redirect:/?opened=" + returnId;
    }

    @PostMapping("/returns/{returnId}/complete")
    public String markCompleted(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        Optional<TaxReturnEntity> taxReturn = findReturnForCurrentYear(returnId);
        if (taxReturn.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Steuerfall wurde nicht gefunden.");
            return "redirect:/";
        }

        TaxReturnEntity entity = taxReturn.get();
        updateStatus(entity, ReturnStatus.COMPLETED, Instant.now());

        redirectAttributes.addFlashAttribute("message", "Steuerfall als abgeschlossen markiert.");
        return "redirect:/?opened=" + returnId;
    }

    @PostMapping("/returns/{returnId}/resume")
    public String markDraft(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        Optional<TaxReturnEntity> taxReturn = findReturnForCurrentYear(returnId);
        if (taxReturn.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Steuerfall wurde nicht gefunden.");
            return "redirect:/";
        }

        TaxReturnEntity entity = taxReturn.get();
        updateStatus(entity, ReturnStatus.DRAFT, null);

        redirectAttributes.addFlashAttribute("message", "Steuerfall wieder als Entwurf markiert.");
        return "redirect:/?opened=" + returnId;
    }

    public String statusLabel(ReturnStatus status) {
        return status.getGermanLabel();
    }

    public String filingModeLabel(FilingMode filingMode) {
        return filingMode.getGermanLabel();
    }

    private TaxReturnEntity createDraftTaxReturn(FilingMode filingMode) {
        TaxReturnEntity taxReturn = new TaxReturnEntity();
        taxReturn.setTaxYear(taxYear);
        taxReturn.setFilingMode(filingMode);
        taxReturn.setStatus(ReturnStatus.DRAFT);
        return taxReturn;
    }

    private Optional<TaxReturnEntity> findReturnForCurrentYear(UUID returnId) {
        return taxReturnRepository.findById(returnId)
                .filter(taxReturn -> taxReturn.getTaxYear() == taxYear);
    }

    private void updateStatus(TaxReturnEntity taxReturn, ReturnStatus status, Instant completedAt) {
        taxReturn.setStatus(status);
        taxReturn.setCompletedAt(completedAt);
        taxReturnRepository.save(taxReturn);
    }
}
