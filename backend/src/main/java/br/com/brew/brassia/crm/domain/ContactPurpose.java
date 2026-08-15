package br.com.brew.brassia.crm.domain;

/**
 * Para que a cervejaria quer falar com a pessoa (CRM-001).
 *
 * <p><strong>Consentimento é por finalidade, e não por pessoa.</strong> Aceitar receber oferta comercial
 * não é aceitar responder pesquisa. Se isto virasse uma única marca de "aceita contato", a primeira
 * pergunta séria sobre base legal derrubaria o modelo inteiro — e derrubaria depois de já haver dado
 * gravado sob ele, que é a hora cara de descobrir.
 *
 * <p>A lista é curta de propósito. Finalidade nova é acréscimo barato; finalidade genérica
 * ("comunicação") é o que esvazia o consentimento, porque autoriza o que ninguém leu.
 */
public enum ContactPurpose {

    /** Aviso de entrega, nota e cobrança — o que a cervejaria precisa mandar para cumprir a venda. */
    TRANSACTIONAL(LegalBasis.CONTRACT),

    /** Oferta, novidade e catálogo. */
    MARKETING(LegalBasis.CONSENT),

    /** Pesquisa de satisfação e retorno sobre o produto. */
    SURVEY(LegalBasis.CONSENT);

    private final LegalBasis basis;

    ContactPurpose(LegalBasis basis) {
        this.basis = basis;
    }

    public LegalBasis basis() {
        return basis;
    }

    /** Se a finalidade exige decisão explícita da pessoa para valer. */
    public boolean requiresConsent() {
        return basis == LegalBasis.CONSENT;
    }
}
