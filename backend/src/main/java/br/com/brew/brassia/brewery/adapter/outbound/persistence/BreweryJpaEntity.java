package br.com.brew.brassia.brewery.adapter.outbound.persistence;

import br.com.brew.brassia.brewery.domain.Brewery;
import br.com.brew.brassia.brewery.domain.BreweryCode;
import br.com.brew.brassia.brewery.domain.BreweryId;
import br.com.brew.brassia.brewery.domain.BreweryName;
import br.com.brew.brassia.brewery.domain.Timezone;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "brewery")
class BreweryJpaEntity {
    @Id private UUID id;
    private String code;
    private String name;
    private String timezone;
    @Version private long version;

    protected BreweryJpaEntity() {}

    static BreweryJpaEntity from(Brewery brewery) {
        var entity = new BreweryJpaEntity();
        entity.id = brewery.id().value();
        entity.code = brewery.code().value();
        entity.name = brewery.name().value();
        entity.timezone = brewery.timezone().value();
        entity.version = brewery.version();
        return entity;
    }

    Brewery toDomain() {
        return Brewery.reconstitute(new BreweryId(id), new BreweryCode(code),
                new BreweryName(name), new Timezone(timezone), version);
    }
}
