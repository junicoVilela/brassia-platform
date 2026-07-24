package br.com.brew.brassia.water.application.port.inbound;

import br.com.brew.brassia.water.domain.ChargeBalance;
import br.com.brew.brassia.water.domain.IonProfile;

/** Calcula o balanço de cargas de um conjunto iônico arbitrário (WTR-003). */
public interface ChargeBalanceUseCase {

    ChargeBalance handle(Query query);

    record Query(IonProfile ions) {}
}
