package de.johannesrabauer.steuererklaerung2025.support;

import de.johannesrabauer.steuererklaerung2025.application.TaxCalculationService;
import de.johannesrabauer.steuererklaerung2025.domain.model.DeductionItemEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.ExportBundleEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.FilingMode;
import de.johannesrabauer.steuererklaerung2025.domain.model.InterviewStep;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.PersonRole;
import de.johannesrabauer.steuererklaerung2025.domain.model.ReturnStatus;
import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.model.WageTaxCertificateEntity;
import java.math.BigDecimal;

public final class TaxReturnFixtureFactory {

    private TaxReturnFixtureFactory() {
    }

    public static TaxReturnEntity completeJointReturn() {
        TaxReturnEntity taxReturn = new TaxReturnEntity();
        taxReturn.setTaxYear(2025);
        taxReturn.setFilingMode(FilingMode.JOINT);
        taxReturn.setStatus(ReturnStatus.DRAFT);
        taxReturn.setCurrentInterviewStep(InterviewStep.REVIEW);
        taxReturn.setStreetAddress("Musterweg 1");
        taxReturn.setPostalCode("10115");
        taxReturn.setCity("Berlin");
        taxReturn.setTaxOffice("Finanzamt Mitte");
        taxReturn.setBankAccountHolder("Anna Muster");
        taxReturn.setIban("DE02120300000000202051");

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

        taxReturn.addWageTaxCertificate(certificate(taxpayer, "Muster GmbH", "50000.00", "8000.00"));
        taxReturn.addWageTaxCertificate(certificate(spouse, "Beispiel AG", "32000.00", "4200.00"));

        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_WORK_EXPENSES, "PENDLERPAUSCHALE", "1500.00");
        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_WORK_EXPENSES, "REISEKOSTEN", "250.00");
        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_WORK_EXPENSES, "ARBEITSMITTEL", "500.00");
        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_WORK_EXPENSES, "GEWERKSCHAFT", "120.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_WORK_EXPENSES, "PENDLERPAUSCHALE", "800.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_WORK_EXPENSES, "REISEKOSTEN", "120.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_WORK_EXPENSES, "ARBEITSMITTEL", "300.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_WORK_EXPENSES, "GEWERKSCHAFT", "50.00");

        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_INSURANCE, "RENTENVERSICHERUNG", "4000.00");
        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_INSURANCE, "KRANKENVERSICHERUNG", "2500.00");
        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_INSURANCE, "ARBEITSLOSENVERSICHERUNG", "500.00");
        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_INSURANCE, "HAFTPFLICHT", "180.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_INSURANCE, "RENTENVERSICHERUNG", "2200.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_INSURANCE, "KRANKENVERSICHERUNG", "1800.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_INSURANCE, "ARBEITSLOSENVERSICHERUNG", "250.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_INSURANCE, "HAFTPFLICHT", "120.00");

        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "SPENDEN", "300.00");
        addDeduction(taxReturn, taxpayer, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "KIRCHENSTEUER", "150.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "SPENDEN", "200.00");
        addDeduction(taxReturn, spouse, TaxCalculationService.CATEGORY_SPECIAL_EXPENSES, "WEITERBILDUNG", "50.00");

        addDeduction(taxReturn, null, TaxCalculationService.CATEGORY_HOUSEHOLD, "HAUSHALTSDIENSTLEISTUNGEN", "1200.00");
        addDeduction(taxReturn, null, TaxCalculationService.CATEGORY_HOUSEHOLD, "HANDWERKERLEISTUNGEN", "800.00");

        addDeduction(taxReturn, null, TaxCalculationService.CATEGORY_EXTRAORDINARY, "KRANKHEITSKOSTEN", "600.00");
        addDeduction(taxReturn, null, TaxCalculationService.CATEGORY_EXTRAORDINARY, "SONSTIGE", "100.00");

        ExportBundleEntity exportBundle = new ExportBundleEntity();
        exportBundle.setElsterShapeVersion("2025-v1");
        exportBundle.setExportStatus("READY");
        taxReturn.setExportBundle(exportBundle);
        return taxReturn;
    }

    public static TaxReturnEntity incompleteSingleReturn() {
        TaxReturnEntity taxReturn = new TaxReturnEntity();
        taxReturn.setTaxYear(2025);
        taxReturn.setFilingMode(FilingMode.SINGLE);
        taxReturn.setStatus(ReturnStatus.DRAFT);
        taxReturn.setCurrentInterviewStep(InterviewStep.BASE_DATA);
        return taxReturn;
    }

    private static WageTaxCertificateEntity certificate(PersonEntity person, String employerName, String grossWages, String wageTax) {
        WageTaxCertificateEntity certificate = new WageTaxCertificateEntity();
        certificate.setPerson(person);
        certificate.setEmployerName(employerName);
        certificate.setGrossWages(new BigDecimal(grossWages));
        certificate.setWageTax(new BigDecimal(wageTax));
        certificate.setSolidaritySurcharge(BigDecimal.ZERO);
        certificate.setChurchTax(BigDecimal.ZERO);
        return certificate;
    }

    private static void addDeduction(TaxReturnEntity taxReturn, PersonEntity person, String category, String subcategory, String amount) {
        DeductionItemEntity deductionItem = new DeductionItemEntity();
        deductionItem.setPerson(person);
        deductionItem.setCategory(category);
        deductionItem.setSubcategory(subcategory);
        deductionItem.setAmount(new BigDecimal(amount));
        deductionItem.setEvidenceStatus("BELEG_VORHANDEN");
        taxReturn.addDeductionItem(deductionItem);
    }
}
