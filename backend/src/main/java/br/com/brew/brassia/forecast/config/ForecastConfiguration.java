package br.com.brew.brassia.forecast.config;

import br.com.brew.brassia.forecast.application.service.CapacityService;
import br.com.brew.brassia.forecast.application.port.outbound.TankCycleRepository;
import br.com.brew.brassia.equipment.EquipmentSummaryLookup;
import br.com.brew.brassia.forecast.application.service.DemandForecastService;
import br.com.brew.brassia.sales.OrderHistoryLookup;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ForecastConfiguration {

    @Bean
    CapacityService capacityService(TankCycleRepository cycles, EquipmentSummaryLookup equipment) {
        return new CapacityService(cycles, equipment);
    }

    @Bean
    DemandForecastService demandForecastService(OrderHistoryLookup history) {
        return new DemandForecastService(history, Clock.systemUTC());
    }
}
