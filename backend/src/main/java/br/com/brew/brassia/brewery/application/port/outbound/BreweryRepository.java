package br.com.brew.brassia.brewery.application.port.outbound;

import br.com.brew.brassia.brewery.domain.Brewery;
import java.util.List;

public interface BreweryRepository {
    boolean existsByCode(String code);
    List<Brewery> findPage(int page, int size);
    long count();
    void save(Brewery brewery);
}
