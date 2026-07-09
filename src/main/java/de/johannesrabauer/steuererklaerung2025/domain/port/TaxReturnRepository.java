package de.johannesrabauer.steuererklaerung2025.domain.port;

import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import java.util.Optional;
import java.util.UUID;

public interface TaxReturnRepository {

    TaxReturnEntity save(TaxReturnEntity taxReturn);

    Optional<TaxReturnEntity> findById(UUID id);
}
