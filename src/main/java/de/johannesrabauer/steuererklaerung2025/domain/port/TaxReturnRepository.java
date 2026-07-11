package de.johannesrabauer.steuererklaerung2025.domain.port;

import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxReturnRepository {

    TaxReturnEntity save(TaxReturnEntity taxReturn);

    List<TaxReturnEntity> findAllByTaxYear(int taxYear);

    Optional<TaxReturnEntity> findById(UUID id);

    void delete(TaxReturnEntity taxReturn);
}
