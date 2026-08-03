package br.com.brew.brassia.quality.domain;

/**
 * Situação do desvio.
 *
 * <p>Nesta história ele apenas <strong>nasce</strong>: conter, investigar, agir e verificar a
 * eficácia são QLT-002. Modelar o tratamento aqui seria antecipar história — e um fluxo de CAPA
 * pela metade é pior que nenhum, porque dá a impressão de que o desvio está sendo tratado.
 */
public enum DeviationStatus {
    OPEN,
    CLOSED
}
