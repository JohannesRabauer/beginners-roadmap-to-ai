# Steuererklaerung 2025 Assistant

Lokale Spring-Boot-Anwendung zur Vorbereitung einer deutschen Einkommensteuererklaerung fuer das Steuerjahr 2025. Die Anwendung fuehrt Schritt fuer Schritt durch die in v1 unterstuetzten Bereiche, berechnet nachvollziehbare Zwischensummen, zeigt Validierungshinweise und erzeugt lokale Exporte.

## Unterstützter v1-Umfang

- Einzel- und gemeinsame Veranlagung fuer Arbeitnehmerinnen und Arbeitnehmer
- Basisdaten, Adresse, Finanzamt und Erstattungs-IBAN
- Einkuenfte aus Lohnsteuerbescheinigungen
- Werbungskosten / Anlage N
- Vorsorgeaufwand
- Sonderausgaben
- Haushaltsnahe Dienstleistungen und Handwerkerleistungen
- Aussergewoehnliche Belastungen in der unterstuetzten v1-Tiefe
- Formorientierte Pruefung mit Validierung und Regelspur
- Lokale PDF-Zusammenfassung
- Lokaler JSON-Export in ELSTER-aehnlicher Struktur

## Nicht enthalten

- Offizielle ELSTER- oder ERiC-Uebermittlung
- Steuerberatung oder rechtliche Beratung
- Selbststaendigkeit, Vermietung, Gewerbe, Kinderlogik oder beliebige Sonderfaelle ausserhalb des beschriebenen v1-Umfangs

## Datenschutz und Sicherheitsmodell

- Die App ist **local-first** und fuer eine lokale Einzelplatznutzung gedacht.
- Beim ersten Start wird eine Passphrase eingerichtet.
- Sensible Felder werden verschluesselt gespeichert.
- Ohne Passphrase koennen vorhandene Daten nicht wiederhergestellt werden.
- Exporte entstehen nur durch ausdrueckliche Benutzeraktion.
- Ein Steuerfall kann aus der Anwendung heraus lokal geloescht werden.

## Voraussetzungen

- Java 25
- Maven 3.9+

## Anwendung starten

```bash
mvn spring-boot:run
```

Danach ist die Anwendung lokal verfuegbar. Beim ersten Start werden `./data/passphrase.properties`, die H2-Datenbank und spaeter `./data/exports/` angelegt.

## Tests ausfuehren

```bash
mvn test
```

## Benutzerablauf

1. Passphrase einrichten oder Anwendung entsperren.
2. Einen Steuerfall fuer 2025 anlegen.
3. Die Interview-Schritte fuer Basisdaten, Einkuenfte und Abzuege durchlaufen.
4. Die formorientierte Pruefung inklusive Validierung und Regelspur ansehen.
5. Optional PDF und JSON lokal erzeugen.
6. Den Steuerfall als abgeschlossen markieren oder lokal loeschen.

## Architekturueberblick

- **Spring Boot + Thymeleaf** fuer Web-UI und lokale Serveranwendung
- **JPA + H2 + Flyway** fuer lokale Persistenz und Migrationen
- **PassphraseService + EncryptionService** fuer Passphrase-Verwaltung und Feldverschluesselung
- **TaxReturnWorkflowService** fuer Wizard-Navigation, Speicherung und Statuspflege
- **TaxValidationService** fuer Pflichtfelder, Plausibilitaet und Exportbereitschaft
- **TaxCalculationService** fuer deterministische 2025-Regeln mit Regelspur
- **TaxExportService** fuer PDF- und JSON-Ausgabe

## Projekt erweitern

Neue Module oder spaetere Steuerjahre sollten an denselben Stellen angeschlossen werden:

1. Migrations- und Domänenmodell erweitern.
2. Neue Wizard-Schritte oder Felder im `TaxReturnWorkflowService` und in den Thymeleaf-Templates verdrahten.
3. Validierung in `TaxValidationService` ergänzen.
4. Regeln und Trace-Ausgaben in `TaxCalculationService` ergänzen.
5. Export-Mapping in `TaxExportService` aktualisieren.
6. Unit- und Integrationstests mit anonymisierten Fixtures erweitern.
