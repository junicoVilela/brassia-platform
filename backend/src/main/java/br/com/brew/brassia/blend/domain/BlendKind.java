package br.com.brew.brassia.blend.domain;

/** O que a operação faz com o volume (BLD-001). */
public enum BlendKind {
    /** Vários lotes viram um: união. */
    MERGE,
    /** Um lote vira vários: divisão. */
    SPLIT
}
