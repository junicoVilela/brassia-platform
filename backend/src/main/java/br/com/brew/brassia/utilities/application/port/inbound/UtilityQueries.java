package br.com.brew.brassia.utilities.application.port.inbound;

import br.com.brew.brassia.utilities.domain.UtilityIndicator;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Leituras do indicador de utilidades (UTL-001). */
public interface UtilityQueries {

    /**
     * O consumo do período, por utilidade, dividido pelo que foi envasado.
     *
     * <p>O período é parâmetro e a resposta é derivada: o mesmo período responde o mesmo enquanto
     * os fatos não mudarem, que é o que o critério chama de reproduzível. Um ciclo registrado com
     * atraso muda o número do mês passado — e deve mudar, porque a água foi gasta.
     */
    Report report(UUID breweryId, Instant from, Instant to);

    record Report(Instant from, Instant to, java.math.BigDecimal packagedLiters,
            List<UtilityIndicator> indicators) {}
}
