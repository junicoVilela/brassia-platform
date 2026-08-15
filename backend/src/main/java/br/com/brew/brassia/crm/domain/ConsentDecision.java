package br.com.brew.brassia.crm.domain;

/** O que a pessoa decidiu sobre uma finalidade (CRM-001). */
public enum ConsentDecision {

    /** Disse que sim. */
    GRANTED,

    /**
     * Disse que não, ou voltou atrás.
     *
     * <p>Não é ausência de decisão: quem nunca decidiu nada também não é contactável, mas os dois casos
     * precisam continuar distinguíveis. "Nunca perguntamos" e "ela pediu para parar" levam a ações
     * opostas quando alguém revisa a base.
     */
    REVOKED
}
