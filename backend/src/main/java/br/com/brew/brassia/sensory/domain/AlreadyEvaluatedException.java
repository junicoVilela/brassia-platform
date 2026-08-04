package br.com.brew.brassia.sensory.domain;

/**
 * O provador já enviou ficha para esta amostra.
 *
 * <p>Um provador, uma ficha: reenviar seria a porta dos fundos para a imutabilidade da avaliação.
 */
public final class AlreadyEvaluatedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String blindCode;

    public AlreadyEvaluatedException(String blindCode) {
        super("já existe ficha sua para a amostra " + blindCode);
        this.blindCode = blindCode;
    }

    public String blindCode() {
        return blindCode;
    }
}
