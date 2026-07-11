package de.johannesrabauer.steuererklaerung2025.domain.model;

public enum FilingMode {
    SINGLE("Einzelveranlagung"),
    JOINT("Gemeinsame Veranlagung");

    private final String germanLabel;

    FilingMode(String germanLabel) {
        this.germanLabel = germanLabel;
    }

    public String getGermanLabel() {
        return germanLabel;
    }
}
