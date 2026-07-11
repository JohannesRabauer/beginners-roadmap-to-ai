package de.johannesrabauer.steuererklaerung2025.domain.model;

import java.util.Arrays;

public enum InterviewStep {
    BASE_DATA("basisdaten", "Basisdaten", "Wir erfassen die Stammdaten fuer den Hauptvordruck und die lokalen Exporte."),
    INCOME("einkuenfte", "Einkuenfte", "Hier uebernehmen Sie die Daten aus der Lohnsteuerbescheinigung."),
    WORK_EXPENSES("werbungskosten", "Werbungskosten", "Wir sammeln die unterstuetzten Anlage-N-Kosten je Person."),
    INSURANCE("vorsorge", "Vorsorgeaufwand", "Hier erfassen Sie die unterstuetzten Versicherungs- und Rentenbeitraege."),
    SPECIAL_EXPENSES("sonderausgaben", "Sonderausgaben", "Spenden, Kirchensteuer und weitere unterstuetzte Sonderausgaben werden hier gesammelt."),
    HOUSEHOLD_SERVICES("haushalt", "Haushaltsnahe Dienstleistungen", "Hier erfassen Sie haushaltsnahe Dienstleistungen und Handwerkerleistungen."),
    EXTRAORDINARY_BURDENS("belastungen", "Aussergewoehnliche Belastungen", "Hier erfassen Sie die in v1 unterstuetzten aussergewoehnlichen Belastungen."),
    REVIEW("pruefung", "Pruefung und Export", "Zum Schluss pruefen wir Vollstaendigkeit, Regeln, Hinweise und lokale Exporte.");

    private final String routeSegment;
    private final String germanLabel;
    private final String explanation;

    InterviewStep(String routeSegment, String germanLabel, String explanation) {
        this.routeSegment = routeSegment;
        this.germanLabel = germanLabel;
        this.explanation = explanation;
    }

    public String getRouteSegment() {
        return routeSegment;
    }

    public String getGermanLabel() {
        return germanLabel;
    }

    public String getExplanation() {
        return explanation;
    }

    public InterviewStep next() {
        int nextIndex = ordinal() + 1;
        if (nextIndex >= values().length) {
            return REVIEW;
        }
        return values()[nextIndex];
    }

    public InterviewStep previous() {
        int previousIndex = ordinal() - 1;
        if (previousIndex < 0) {
            return BASE_DATA;
        }
        return values()[previousIndex];
    }

    public int progressPercent() {
        if (values().length == 1) {
            return 100;
        }
        return Math.round((ordinal() * 100.0f) / (values().length - 1));
    }

    public static InterviewStep fromRouteSegment(String routeSegment) {
        return Arrays.stream(values())
                .filter(step -> step.routeSegment.equalsIgnoreCase(routeSegment))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported interview step: " + routeSegment));
    }
}
