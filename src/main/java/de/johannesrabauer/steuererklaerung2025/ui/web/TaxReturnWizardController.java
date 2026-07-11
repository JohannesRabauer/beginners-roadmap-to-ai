package de.johannesrabauer.steuererklaerung2025.ui.web;

import de.johannesrabauer.steuererklaerung2025.application.TaxCalculationService;
import de.johannesrabauer.steuererklaerung2025.application.TaxExportService;
import de.johannesrabauer.steuererklaerung2025.application.TaxReturnWorkflowService;
import de.johannesrabauer.steuererklaerung2025.domain.model.InterviewStep;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonRole;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationIssueEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ValidationSeverity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.util.MultiValueMap;

@Controller
public class TaxReturnWizardController {

    private final TaxReturnWorkflowService workflowService;
    private final TaxCalculationService calculationService;
    private final TaxExportService exportService;
    private final String applicationName;

    public TaxReturnWizardController(
            TaxReturnWorkflowService workflowService,
            TaxCalculationService calculationService,
            TaxExportService exportService,
            @Value("${steuererklaerung.ui-name}") String applicationName) {
        this.workflowService = workflowService;
        this.calculationService = calculationService;
        this.exportService = exportService;
        this.applicationName = applicationName;
    }

    @GetMapping("/returns/{returnId}/wizard")
    public String wizardIndex(@PathVariable("returnId") UUID returnId) {
        TaxReturnEntity taxReturn = workflowService.requireReturn(returnId);
        return "redirect:/returns/" + returnId + "/wizard/" + taxReturn.getCurrentInterviewStep().getRouteSegment();
    }

    @GetMapping("/returns/{returnId}/wizard/{step}")
    public String wizardStep(@PathVariable("returnId") UUID returnId, @PathVariable("step") String step, Model model) {
        TaxReturnEntity taxReturn = workflowService.requireReturn(returnId);
        InterviewStep interviewStep = InterviewStep.fromRouteSegment(step);
        populateWizardModel(model, taxReturn, interviewStep);
        return interviewStep == InterviewStep.REVIEW ? "review" : "wizard";
    }

    @PostMapping("/returns/{returnId}/wizard/{step}")
    public String saveWizardStep(
            @PathVariable("returnId") UUID returnId,
            @PathVariable("step") String step,
            @RequestParam("navigationAction") String navigationAction,
            @RequestParam MultiValueMap<String, String> formData,
            RedirectAttributes redirectAttributes) {
        InterviewStep interviewStep = InterviewStep.fromRouteSegment(step);
        InterviewStep nextStep = resolveNextStep(interviewStep, navigationAction);
        workflowService.saveStep(returnId, interviewStep, formData, nextStep);
        redirectAttributes.addFlashAttribute("message", "Der Abschnitt wurde lokal gespeichert.");
        return "redirect:/returns/" + returnId + "/wizard/" + nextStep.getRouteSegment();
    }

    @PostMapping("/returns/{returnId}/review/complete")
    public String completeReturn(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        try {
            workflowService.markCompleted(returnId);
            redirectAttributes.addFlashAttribute("message", "Der Steuerfall wurde als abgeschlossen markiert.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/returns/" + returnId + "/wizard/" + InterviewStep.REVIEW.getRouteSegment();
    }

    @PostMapping("/returns/{returnId}/review/resume")
    public String resumeReturn(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        workflowService.markDraft(returnId);
        redirectAttributes.addFlashAttribute("message", "Der Steuerfall ist wieder als Entwurf geoeffnet.");
        return "redirect:/returns/" + returnId + "/wizard/" + InterviewStep.REVIEW.getRouteSegment();
    }

    @PostMapping("/returns/{returnId}/exports/pdf")
    public String exportPdf(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        TaxReturnEntity taxReturn = workflowService.requireReturn(returnId);
        Path pdfPath = exportService.generatePdfSummary(taxReturn, taxReturn.getValidationIssues());
        workflowService.save(taxReturn);
        redirectAttributes.addFlashAttribute("message", "PDF-Zusammenfassung erstellt: " + pdfPath);
        return "redirect:/returns/" + returnId + "/wizard/" + InterviewStep.REVIEW.getRouteSegment();
    }

    @PostMapping("/returns/{returnId}/exports/elster")
    public String exportElster(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        TaxReturnEntity taxReturn = workflowService.requireReturn(returnId);
        Path exportPath = exportService.generateElsterShapeExport(taxReturn, taxReturn.getValidationIssues());
        workflowService.save(taxReturn);
        redirectAttributes.addFlashAttribute("message", "Strukturierter Export erstellt: " + exportPath);
        return "redirect:/returns/" + returnId + "/wizard/" + InterviewStep.REVIEW.getRouteSegment();
    }

    @PostMapping("/returns/{returnId}/delete")
    public String deleteReturn(@PathVariable("returnId") UUID returnId, RedirectAttributes redirectAttributes) {
        workflowService.deleteReturn(returnId);
        redirectAttributes.addFlashAttribute("message", "Der lokale Steuerfall wurde geloescht.");
        return "redirect:/";
    }

    public String severityLabel(ValidationSeverity severity) {
        return severity.getGermanLabel();
    }

    public String personLabel(PersonRole role) {
        return role == PersonRole.SPOUSE ? "Ehepartner/in" : "Steuerpflichtige Person";
    }

    public boolean hasRole(TaxReturnEntity taxReturn, PersonRole role) {
        return taxReturn.findPerson(role).isPresent();
    }

    public String evidenceLabel(String evidenceStatus) {
        return switch (evidenceStatus == null ? "" : evidenceStatus) {
            case "BELEG_VORHANDEN" -> "Beleg vorhanden";
            case "NACHREICHEN" -> "Beleg wird nachgereicht";
            default -> "Nicht angegeben";
        };
    }

    private void populateWizardModel(Model model, TaxReturnEntity taxReturn, InterviewStep step) {
        TaxCalculationService.ReviewSummary reviewSummary = calculationService.buildReviewSummary(taxReturn, taxReturn.getValidationIssues());
        model.addAttribute("applicationName", applicationName);
        model.addAttribute("taxReturn", taxReturn);
        model.addAttribute("taxpayer", taxReturn.findPerson(PersonRole.TAXPAYER).orElse(null));
        model.addAttribute("spouse", taxReturn.findPerson(PersonRole.SPOUSE).orElse(null));
        model.addAttribute("step", step);
        model.addAttribute("stepStatuses", workflowService.buildStepStatuses(taxReturn));
        model.addAttribute("progressPercent", step.progressPercent());
        model.addAttribute("reviewSummary", reviewSummary);
        model.addAttribute("validationIssues", taxReturn.getValidationIssues());
        model.addAttribute("errorCount", reviewSummary.errorCount());
        model.addAttribute("warningCount", reviewSummary.warningCount());
        model.addAttribute("incomeRows", workflowService.buildIncomeRows(taxReturn));
        model.addAttribute("workValues", workflowService.deductionValues(taxReturn, TaxCalculationService.CATEGORY_WORK_EXPENSES));
        model.addAttribute("workEvidence", workflowService.evidenceValues(taxReturn, TaxCalculationService.CATEGORY_WORK_EXPENSES));
        model.addAttribute("insuranceValues", workflowService.deductionValues(taxReturn, TaxCalculationService.CATEGORY_INSURANCE));
        model.addAttribute("insuranceEvidence", workflowService.evidenceValues(taxReturn, TaxCalculationService.CATEGORY_INSURANCE));
        model.addAttribute("specialValues", workflowService.deductionValues(taxReturn, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES));
        model.addAttribute("specialEvidence", workflowService.evidenceValues(taxReturn, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES));
        model.addAttribute("householdValues", workflowService.deductionValues(taxReturn, TaxCalculationService.CATEGORY_HOUSEHOLD));
        model.addAttribute("householdEvidence", workflowService.evidenceValues(taxReturn, TaxCalculationService.CATEGORY_HOUSEHOLD));
        model.addAttribute("extraordinaryValues", workflowService.deductionValues(taxReturn, TaxCalculationService.CATEGORY_EXTRAORDINARY));
        model.addAttribute("extraordinaryEvidence", workflowService.evidenceValues(taxReturn, TaxCalculationService.CATEGORY_EXTRAORDINARY));
    }

    private InterviewStep resolveNextStep(InterviewStep currentStep, String navigationAction) {
        if ("back".equalsIgnoreCase(navigationAction)) {
            return currentStep.previous();
        }
        if ("save".equalsIgnoreCase(navigationAction)) {
            return currentStep;
        }
        return currentStep.next();
    }
}
