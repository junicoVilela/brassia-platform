package br.com.brew.brassia.blend.domain;

/** Onde a operação está (BLD-001). */
public enum BlendStatus {
    /** Simulada: o balanço já fecha, mas nada mudou de tanque. */
    SIMULATED,
    /** Aprovada: alguém com alçada assumiu a operação. Ainda não executada. */
    APPROVED,
    /** Executada: a genealogia passou a valer e o recall alcança os dois lados. */
    EXECUTED,
    /** Descartada. Fica no histórico — ver BlendOperation#discard. */
    DISCARDED
}
