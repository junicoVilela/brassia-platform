package br.com.brew.brassia.traceability.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.traceability.DestinationSource;
import br.com.brew.brassia.traceability.LineageSource;
import br.com.brew.brassia.traceability.QuarantineCheck;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineCommands;
import br.com.brew.brassia.traceability.application.port.inbound.QuarantineQueries;
import br.com.brew.brassia.traceability.application.port.inbound.DrillCommands;
import br.com.brew.brassia.traceability.application.port.inbound.DrillQueries;
import br.com.brew.brassia.traceability.application.port.inbound.RecallCommands;
import br.com.brew.brassia.traceability.application.port.inbound.RecallQueries;
import br.com.brew.brassia.traceability.application.port.inbound.TraceabilityQueries;
import br.com.brew.brassia.traceability.application.port.outbound.DrillRepository;
import br.com.brew.brassia.traceability.application.port.outbound.QuarantineRepository;
import br.com.brew.brassia.traceability.application.port.outbound.RecallRepository;
import br.com.brew.brassia.traceability.application.service.DrillHandlers;
import br.com.brew.brassia.traceability.application.service.DrillQueryHandler;
import br.com.brew.brassia.traceability.application.service.GenealogyQueryHandler;
import br.com.brew.brassia.traceability.application.service.QuarantineHandlers;
import br.com.brew.brassia.traceability.application.service.QuarantineQueryHandler;
import br.com.brew.brassia.traceability.application.service.RecallHandlers;
import br.com.brew.brassia.traceability.application.service.RecallQueryHandler;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A consulta é só leitura, então não há transação a abrir: cada fonte responde com a sua própria
 * consulta e o domínio junta. Sem escrita, não há o que tornar atômico.
 *
 * <p>A quarentena escreve, e aí há: abrir verifica a inexistência de outra aberta e insere no mesmo
 * commit — sem isso, dois pedidos simultâneos passariam os dois pela verificação. O índice único
 * parcial no banco é a segunda linha de defesa, e a que vale de verdade.
 */
@Configuration(proxyBeanMethods = false)
class TraceabilityConfiguration {

    /** Recebe todas as fontes de linhagem registradas no contexto — inclusive as que ainda não existem. */
    @Bean
    TraceabilityQueries traceabilityQueries(List<LineageSource> sources) {
        return new GenealogyQueryHandler(sources);
    }

    /**
     * Um bean só, dos dois lados. O mesmo objeto responde à tela ({@link QuarantineQueries}) e aos
     * módulos que bloqueiam ({@link QuarantineCheck}): são a mesma pergunta feita de dois lados, e
     * duas implementações acabariam divergindo — a tela mostraria um descendente que o envase
     * deixou passar. Por isso o tipo declarado é o concreto: registrar um bean por interface daria
     * dois candidatos para a mesma injeção.
     */
    @Bean
    QuarantineQueryHandler quarantineQueryHandler(QuarantineRepository quarantines,
            List<LineageSource> sources) {
        return new QuarantineQueryHandler(quarantines, sources);
    }

    @Bean
    QuarantineCommands.Open openQuarantineUseCase(QuarantineRepository quarantines,
            List<LineageSource> sources, AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new QuarantineHandlers.Open(quarantines, sources, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, type, nodeId, reason) -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, type, nodeId, reason)));
    }

    @Bean
    QuarantineCommands.Release releaseQuarantineUseCase(QuarantineRepository quarantines, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new QuarantineHandlers.Release(quarantines, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, quarantineId, justification) -> transaction.executeWithoutResult(
                status -> handler.handle(actorId, breweryId, quarantineId, justification));
    }

    /** Consulta pura: o escopo é derivado, e o que está guardado é só a comunicação. */
    @Bean
    RecallQueries recallQueries(RecallRepository recalls, List<LineageSource> sources,
            List<DestinationSource> destinations) {
        return new RecallQueryHandler(recalls, sources, destinations);
    }

    /**
     * Abrir o recall grava o cabeçalho e uma linha por destino alcançado no mesmo commit: um recall
     * sem a lista de quem avisar seria um recall que ninguém consegue executar.
     */
    @Bean
    RecallCommands.Open openRecallUseCase(RecallRepository recalls, List<LineageSource> sources,
            List<DestinationSource> destinations, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new RecallHandlers.Open(recalls, sources, destinations, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, type, nodeId, reason) -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, type, nodeId, reason)));
    }

    @Bean
    RecallCommands.RecordNotification recordRecallNotificationUseCase(RecallRepository recalls,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new RecallHandlers.RecordNotification(recalls, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, recallId, notificationId, channel, note) ->
                transaction.executeWithoutResult(
                        status -> handler.handle(actorId, breweryId, recallId, notificationId, channel, note));
    }

    @Bean
    RecallCommands.Close closeRecallUseCase(RecallRepository recalls, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new RecallHandlers.Close(recalls, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, recallId, summary) -> transaction.executeWithoutResult(
                status -> handler.handle(actorId, breweryId, recallId, summary));
    }

    /** O relatório é leitura pura; o que ele mostra enquanto o simulado corre é o alvo, não o placar. */
    @Bean
    DrillQueries drillQueries(DrillRepository drills, List<LineageSource> sources,
            List<DestinationSource> destinations) {
        return new DrillQueryHandler(drills, sources, destinations);
    }

    @Bean
    DrillCommands.Start startRecallDrillUseCase(DrillRepository drills, List<LineageSource> sources,
            AuditTrail audit, PlatformTransactionManager transactionManager) {
        var handler = new DrillHandlers.Start(drills, sources, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, type, nodeId, note) -> Objects.requireNonNull(
                transaction.execute(status -> handler.handle(actorId, breweryId, type, nodeId, note)));
    }

    /**
     * Encerrar tira a medição e a congela no mesmo commit: um simulado encerrado com número medido
     * meia hora depois seria um relatório sobre outro momento.
     */
    @Bean
    DrillCommands.Finish finishRecallDrillUseCase(DrillRepository drills, List<LineageSource> sources,
            List<DestinationSource> destinations, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new DrillHandlers.Finish(drills, sources, destinations, audit);
        var transaction = new TransactionTemplate(transactionManager);
        return (actorId, breweryId, drillId, unitsLocated, summary, actions) ->
                transaction.executeWithoutResult(status ->
                        handler.handle(actorId, breweryId, drillId, unitsLocated, summary, actions));
    }
}
