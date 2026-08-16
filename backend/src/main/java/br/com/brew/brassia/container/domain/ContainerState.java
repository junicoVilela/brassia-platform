package br.com.brew.brassia.container.domain;

/**
 * Onde o contêiner está no ciclo.
 *
 * <p><strong>{@code RETURNED} não é {@code EMPTY}.</strong> O que voltou do cliente está sujo até que
 * alguém diga o contrário — e a diferença entre os dois estados é exatamente o que impede um keg de
 * voltar direto para a enchedeira sem passar pela limpeza.
 */
public enum ContainerState {
    /** Vazio, limpo e disponível para encher. */
    EMPTY,
    FILLED,
    IN_TRANSIT,
    AT_CUSTOMER,
    /** De volta na casa, ainda não liberado: sujo até prova em contrário. */
    RETURNED,
    IN_MAINTENANCE,
    /** Terminal. */
    RETIRED
}
