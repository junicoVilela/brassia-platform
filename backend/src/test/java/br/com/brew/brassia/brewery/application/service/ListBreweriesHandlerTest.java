package br.com.brew.brassia.brewery.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.brewery.application.port.inbound.ListBreweriesUseCase.Query;
import br.com.brew.brassia.brewery.application.port.outbound.BreweryRepository;
import br.com.brew.brassia.brewery.domain.Brewery;
import br.com.brew.brassia.brewery.domain.BreweryCode;
import br.com.brew.brassia.brewery.domain.BreweryName;
import br.com.brew.brassia.brewery.domain.Timezone;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListBreweriesHandlerTest {

    @Test
    void mapsSummariesAndComputesPagination() {
        var repo = new FakeBreweries(List.of(brewery("SB40", "Casa Brew")), 3);

        var result = new ListBreweriesHandler(repo).handle(new Query(0, 2));

        assertThat(result.content()).singleElement().satisfies(s -> {
            assertThat(s.code()).isEqualTo("SB40");
            assertThat(s.timezone()).isEqualTo("America/Sao_Paulo");
        });
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2); // ceil(3/2)
    }

    private static Brewery brewery(String code, String name) {
        return Brewery.register(new BreweryCode(code), new BreweryName(name), new Timezone("America/Sao_Paulo"));
    }

    private record FakeBreweries(List<Brewery> page, long total) implements BreweryRepository {
        @Override public boolean existsByCode(String code) { return false; }
        @Override public List<Brewery> findPage(int p, int s) { return page; }
        @Override public long count() { return total; }
        @Override public void save(Brewery brewery) { }
    }
}
