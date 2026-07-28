package br.com.brew.brassia.fermentation.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.fermentation.application.port.inbound.CreateProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.GetProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.ListProfilesUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.PublishProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.inbound.UpdateProfileUseCase;
import br.com.brew.brassia.fermentation.application.port.outbound.ProfileRepository;
import br.com.brew.brassia.fermentation.application.service.CreateProfileHandler;
import br.com.brew.brassia.fermentation.application.service.GetProfileHandler;
import br.com.brew.brassia.fermentation.application.service.ListProfilesHandler;
import br.com.brew.brassia.fermentation.application.service.PublishProfileHandler;
import br.com.brew.brassia.fermentation.application.service.UpdateProfileHandler;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
class FermentationConfiguration {

    @Bean
    CreateProfileUseCase createProfileUseCase(
            ProfileRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new CreateProfileHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> Objects.requireNonNull(transaction.execute(status -> handler.handle(command)));
    }

    @Bean
    UpdateProfileUseCase updateProfileUseCase(
            ProfileRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new UpdateProfileHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    PublishProfileUseCase publishProfileUseCase(
            ProfileRepository repository, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new PublishProfileHandler(repository, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return command -> transaction.executeWithoutResult(status -> handler.handle(command));
    }

    @Bean
    ListProfilesUseCase listProfilesUseCase(ProfileRepository repository) {
        return new ListProfilesHandler(repository);
    }

    @Bean
    GetProfileUseCase getProfileUseCase(ProfileRepository repository) {
        return new GetProfileHandler(repository);
    }
}
