package br.com.brew.brassia.crm.config;

import br.com.brew.brassia.crm.application.port.inbound.CustomerCommands;
import br.com.brew.brassia.crm.application.port.outbound.ContactRepository;
import br.com.brew.brassia.crm.application.port.outbound.CustomerRepository;
import br.com.brew.brassia.crm.application.port.outbound.RetentionPolicyRepository;
import br.com.brew.brassia.crm.application.service.CustomerHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CrmConfiguration {

    @Bean
    CustomerCommands customerCommands(CustomerRepository customers, ContactRepository contacts,
            RetentionPolicyRepository retention) {
        return new CustomerHandlers(customers, contacts, retention);
    }
}
