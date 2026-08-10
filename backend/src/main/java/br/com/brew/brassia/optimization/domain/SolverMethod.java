package br.com.brew.brassia.optimization.domain;

/**
 * Como a busca foi feita (OPT-001).
 *
 * <p>Viaja com o resultado porque um número sem método não se reproduz — e um resultado que não se
 * reproduz não se audita. Trocar de método muda a resposta, e quem lê um resultado de seis meses atrás
 * precisa saber qual estava valendo.
 */
public enum SolverMethod {

    /**
     * Enumeração exaustiva das substituições candidatas, uma por vez.
     *
     * <p><strong>Determinístico e sem semente:</strong> percorre todas as candidatas na ordem estável do
     * catálogo e devolve sempre o mesmo resultado para a mesma entrada. É o único método hoje, e é
     * deliberado — enquanto o espaço de busca couber na enumeração, um método aleatório introduziria
     * variação que ninguém pediu num resultado que precisa ser reproduzível.
     */
    EXHAUSTIVE_SINGLE_SUBSTITUTION;

    /** Se este método usa semente. Falso aqui, e o resultado registra a ausência em vez de omiti-la. */
    public boolean usesSeed() {
        return false;
    }
}
