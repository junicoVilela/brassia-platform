package br.com.brew.brassia.production.domain;

/** Estado de uma etapa do roteiro (PRD-002): pendente, ativa (em execução) ou concluída. */
public enum BatchStepStatus {
    PENDING,
    ACTIVE,
    DONE
}
