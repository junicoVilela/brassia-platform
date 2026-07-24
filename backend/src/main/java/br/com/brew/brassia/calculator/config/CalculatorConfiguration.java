package br.com.brew.brassia.calculator.config;

import br.com.brew.brassia.calculator.domain.Calculators;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CalculatorConfiguration {

    @Bean
    Calculators calculators() {
        return new Calculators();
    }
}
