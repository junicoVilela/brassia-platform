package br.com.brew.brassia.crm.application.port.outbound;

import br.com.brew.brassia.crm.domain.Contact;
import br.com.brew.brassia.crm.domain.ConsentEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistência dos contatos e do livro de consentimento (CRM-001).
 *
 * <p>O contato e as suas decisões são um agregado só: quem carrega a pessoa carrega o que ela decidiu,
 * porque responder "podia mandar aquilo?" sem o histórico é impossível. Por isso não há repositório
 * separado para consentimento — ele não tem vida fora do contato.
 *
 * <p>{@link #appendConsent} existe em vez de um {@code save} que regrava tudo: o livro <strong>só
 * cresce</strong>, e uma operação que regravasse a lista inteira permitiria, por descuido, sumir com uma
 * decisão antiga — que é exatamente a prova que o livro existe para guardar.
 */
public interface ContactRepository {

    void insert(Contact contact, UUID actorId);

    Optional<Contact> find(UUID breweryId, UUID id);

    List<Contact> listByCustomer(UUID breweryId, UUID customerId);

    void appendConsent(UUID breweryId, UUID contactId, ConsentEntry entry);

    /** Grava o apagamento: limpa os campos pessoais e marca quando foi. */
    void anonymize(Contact contact);
}
