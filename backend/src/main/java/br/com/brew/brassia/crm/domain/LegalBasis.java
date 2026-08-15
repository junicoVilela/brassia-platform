package br.com.brew.brassia.crm.domain;

/**
 * Sobre o que se apoia o direito de falar com uma pessoa (CRM-001).
 *
 * <p><strong>Existe porque consentimento não é a única base legal, e tratar como se fosse quebra a
 * operação.</strong> Se todo contato exigisse consentimento, revogar a permissão de receber oferta
 * comercial derrubaria junto o aviso de que a entrega saiu — e a cervejaria ficaria proibida de cumprir
 * o que vendeu. As duas coisas têm fundamentos diferentes e precisam poder ser revogadas em separado.
 */
public enum LegalBasis {

    /**
     * O contato é necessário para executar o que foi contratado: aviso de entrega, nota, cobrança.
     *
     * <p>Não se pede consentimento para isto, e por isso <strong>não se revoga</strong> — quem não quer
     * mais receber aviso de entrega cancela o pedido, não o aviso. O que continua valendo é o
     * apagamento: contato anonimizado não recebe nada, nem transacional.
     */
    CONTRACT,

    /**
     * O contato só é lícito se a pessoa disse que sim: oferta, novidade, pesquisa.
     *
     * <p>Silêncio é não. A ausência de decisão nunca vale como permissão, o que é o motivo de
     * {@code allows} responder {@code false} para quem nunca decidiu nada.
     */
    CONSENT
}
