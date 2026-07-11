package de.johannesrabauer.steuererklaerung2025.domain.model;

public enum ValidationSeverity {
    ERROR("Fehler"),
    WARNING("Hinweis");

    private final String germanLabel;

    ValidationSeverity(String germanLabel) {
        this.germanLabel = germanLabel;
    }

    public String getGermanLabel() {
        return germanLabel;
    }
}
