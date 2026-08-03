package br.com.brew.brassia.quality.domain;

/**
 * Rascunho não julga medição. Um plano em edição pode ter limite pela metade, e aprovar ou
 * reprovar contra ele produziria veredito que muda sozinho quando alguém salvar o rascunho.
 */
public final class PlanNotPublishedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String planCode;

    public PlanNotPublishedException(String planCode) {
        super("o plano %s ainda é rascunho e não julga medição".formatted(planCode));
        this.planCode = planCode;
    }

    public String planCode() {
        return planCode;
    }
}
