package br.com.brew.brassia.quality.domain;

/**
 * Rascunho editável, publicado imutável — mesmo padrão do perfil de fermentação (FER-001) e do
 * modelo de rótulo (PKG-004).
 *
 * <p>Sem isso, mexer no plano reescreveria o veredito de lotes já julgados: uma medição aprovada
 * ontem poderia virar desvio hoje porque alguém apertou um limite.
 */
public enum ControlPlanStatus {
    DRAFT,
    PUBLISHED;

    public boolean judges() {
        return this == PUBLISHED;
    }
}
