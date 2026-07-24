package br.com.brew.brassia.water.application.service;

import br.com.brew.brassia.water.application.port.inbound.ChargeBalanceUseCase;
import br.com.brew.brassia.water.domain.ChargeBalance;

public final class ChargeBalanceHandler implements ChargeBalanceUseCase {

    @Override
    public ChargeBalance handle(Query query) {
        return ChargeBalance.of(query.ions());
    }
}
