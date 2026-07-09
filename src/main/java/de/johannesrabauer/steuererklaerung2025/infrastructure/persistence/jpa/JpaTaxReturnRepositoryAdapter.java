package de.johannesrabauer.steuererklaerung2025.infrastructure.persistence.jpa;

import de.johannesrabauer.steuererklaerung2025.domain.model.TaxReturnEntity;
import de.johannesrabauer.steuererklaerung2025.domain.port.TaxReturnRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTaxReturnRepositoryAdapter implements TaxReturnRepository {

    private final SpringDataTaxReturnRepository repository;

    public JpaTaxReturnRepositoryAdapter(SpringDataTaxReturnRepository repository) {
        this.repository = repository;
    }

    @Override
    public TaxReturnEntity save(TaxReturnEntity taxReturn) {
        return repository.save(taxReturn);
    }

    @Override
    public Optional<TaxReturnEntity> findById(UUID id) {
        return repository.findById(id);
    }
}
