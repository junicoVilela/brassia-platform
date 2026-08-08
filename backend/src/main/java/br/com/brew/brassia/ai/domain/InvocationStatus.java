package br.com.brew.brassia.ai.domain;

/**
 * Como uma chamada ao modelo terminou (AIA-001).
 *
 * <p>As falhas são estados nomeados, não um {@code false} genérico: "não respondeu" e "respondeu
 * fora do contrato" pedem providências diferentes de quem opera — a primeira é do provedor, a
 * segunda é do nosso prompt.
 */
public enum InvocationStatus {

    /** Respondeu e a resposta satisfez o contrato. */
    SUCCEEDED,

    /** Provedor desligado por configuração. Estado normal, não incidente. */
    PROVIDER_DISABLED,

    /** Provedor recusou, errou ou não respondeu no prazo. */
    PROVIDER_FAILED,

    /** Respondeu, mas a resposta não satisfez o contrato — recusada inteira. */
    REJECTED_CONTRACT,

    /** Não foi chamado: o orçamento do mês já estava esgotado. */
    BUDGET_EXCEEDED;

    public boolean billable() {
        return this == SUCCEEDED || this == REJECTED_CONTRACT || this == PROVIDER_FAILED;
    }
}
