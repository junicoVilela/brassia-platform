package br.com.brew.brassia.brewery.adapter.outbound.persistence;

import br.com.brew.brassia.brewery.BreweryDirectory;
import br.com.brew.brassia.brewery.BreweryRef;
import br.com.brew.brassia.brewery.domain.Brewery;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
class JpaBreweryDirectory implements BreweryDirectory {
    private final SpringDataBreweryJpaRepository repository;

    JpaBreweryDirectory(SpringDataBreweryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<BreweryRef> findAll() {
        return repository.findAll(Sort.by("code")).stream()
                .map(BreweryJpaEntity::toDomain)
                .map(JpaBreweryDirectory::toRef)
                .toList();
    }

    @Override
    public Optional<BreweryRef> findById(UUID id) {
        return repository.findById(id).map(BreweryJpaEntity::toDomain).map(JpaBreweryDirectory::toRef);
    }

    private static BreweryRef toRef(Brewery brewery) {
        return new BreweryRef(brewery.id().value(), brewery.code().value(), brewery.name().value());
    }
}
