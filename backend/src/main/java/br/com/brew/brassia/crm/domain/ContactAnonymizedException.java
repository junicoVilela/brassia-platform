package br.com.brew.brassia.crm.domain;

/**
 * Tentaram mexer numa pessoa que não existe mais na base (CRM-001).
 *
 * <p>Anonimizar é irreversível por definição — se desse para voltar, o dado não teria sido apagado.
 * Depois disso não há a quem pedir consentimento nem a quem escrever, e a operação é recusada em vez de
 * ignorada em silêncio: quem chamou está trabalhando com uma ideia errada do estado, e vai continuar
 * errado no próximo passo se nada reclamar.
 */
public class ContactAnonymizedException extends RuntimeException {

    public ContactAnonymizedException(String message) {
        super(message);
    }
}
