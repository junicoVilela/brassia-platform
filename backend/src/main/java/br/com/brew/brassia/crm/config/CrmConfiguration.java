package br.com.brew.brassia.crm.config;

import br.com.brew.brassia.sales.CustomerActivityLookup;
import br.com.brew.brassia.distribution.CustomerDeliveryLookup;
import br.com.brew.brassia.crm.application.service.RetentionQueueService;
import br.com.brew.brassia.crm.application.port.inbound.CustomerCommands;
import br.com.brew.brassia.crm.application.port.outbound.ContactRepository;
import br.com.brew.brassia.crm.application.port.outbound.CustomerRepository;
import br.com.brew.brassia.crm.application.port.outbound.RetentionPolicyRepository;
import br.com.brew.brassia.crm.application.service.CustomerHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CrmConfiguration {

    /** A fila de retenção: lista quem venceu, e não apaga ninguém (DUV-CRM-001). */
    @Bean
    RetentionQueueService retentionQueueService(ContactRepository contacts,
            RetentionPolicyRepository policies, CustomerActivityLookup orders,
            CustomerDeliveryLookup deliveries) {
        return new RetentionQueueService(contacts, policies, orders, deliveries);
    }

    @Bean
    CustomerCommands customerCommands(CustomerRepository customers, ContactRepository contacts,
            RetentionPolicyRepository retention) {
        return new CustomerHandlers(customers, contacts, retention);
    }
}
