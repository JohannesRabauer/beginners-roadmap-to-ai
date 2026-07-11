package de.johannesrabauer.steuererklaerung2025.infrastructure.persistence.jpa;

import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataTaxReturnRepository extends JpaRepository<TaxReturnEntity, UUID> {

    List<TaxReturnEntity> findAllByTaxYearOrderByUpdatedAtDesc(int taxYear);

    @EntityGraph(attributePaths = {
            "persons",
            "wageTaxCertificates",
            "deductionItems",
            "validationIssues",
            "exportBundle"
    })
    Optional<TaxReturnEntity> findById(UUID id);
}
