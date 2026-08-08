package br.com.brew.brassia.knowledge.config;

import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.knowledge.KnowledgeRetrieval;
import br.com.brew.brassia.knowledge.application.port.inbound.DocumentCommands;
import br.com.brew.brassia.knowledge.application.port.inbound.DocumentQueries;
import br.com.brew.brassia.knowledge.application.port.outbound.DocumentRepository;
import br.com.brew.brassia.knowledge.application.service.DocumentHandlers;
import br.com.brew.brassia.knowledge.domain.Chunker;
import java.time.Clock;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Composição da base de conhecimento (RAG-001).
 *
 * <p><strong>Indexar é uma transação só, e é o ponto da história.</strong> A versão nova entra e a antiga
 * é encerrada juntas: se as duas escritas pudessem falhar em separado, existiria uma janela com duas
 * versões vigentes do mesmo documento — e a recuperação devolveria duas respostas conflitantes sem meio
 * de escolher. É durante essa janela que alguém consulta.
 */
@Configuration(proxyBeanMethods = false)
class KnowledgeConfiguration {

    @Bean
    DocumentCommands documentCommands(DocumentRepository documents, AuditTrail audit,
            PlatformTransactionManager transactionManager) {
        var handler = new DocumentHandlers.Index(documents, Chunker.standard(), audit, Clock.systemUTC());
        var transaction = new TransactionTemplate(transactionManager);
        return request -> Objects.requireNonNull(
                transaction.execute(status -> handler.index(request)));
    }

    @Bean
    DocumentQueries documentQueries(DocumentRepository documents) {
        return new DocumentHandlers.Listing(documents);
    }

    @Bean
    KnowledgeRetrieval knowledgeRetrieval(DocumentRepository documents) {
        return new DocumentHandlers.Retrieval(documents);
    }
}
