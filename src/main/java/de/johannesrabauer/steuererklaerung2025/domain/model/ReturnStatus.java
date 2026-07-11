package de.johannesrabauer.steuererklaerung2025.domain.model;

public enum ReturnStatus {
    DRAFT("Entwurf"),
    COMPLETED("Abgeschlossen");

    private final String germanLabel;

    ReturnStatus(String germanLabel) {
        this.germanLabel = germanLabel;
    }

    public String getGermanLabel() {
        return germanLabel;
    }
}
