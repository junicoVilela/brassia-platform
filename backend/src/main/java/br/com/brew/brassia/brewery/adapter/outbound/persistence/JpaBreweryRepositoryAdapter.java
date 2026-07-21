package br.com.brew.brassia.brewery.adapter.outbound.persistence;

import br.com.brew.brassia.brewery.application.port.outbound.BreweryRepository;
import br.com.brew.brassia.brewery.domain.Brewery;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

// Não pode ser final: @Repository é proxiado (CGLIB) para tradução de exceções.
@Repository
class JpaBreweryRepositoryAdapter implements BreweryRepository {
    private final SpringDataBreweryJpaRepository repository;

    JpaBreweryRepositoryAdapter(SpringDataBreweryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByCode(String code) {
        return repository.existsByCode(code);
    }

    @Override
    public List<Brewery> findPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, Sort.by("code")))
                .map(BreweryJpaEntity::toDomain)
                .getContent();
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public void save(Brewery brewery) {
        repository.save(BreweryJpaEntity.from(brewery));
    }
}
