package br.com.brew.brassia.production.domain;

/**
 * De onde o lote nasceu (PRD-001; blend a partir da DEC-BLD-003).
 *
 * <p>A ausência de ordem já diria que o lote não veio de uma ordem, mas não diria <em>por quê</em> — lote
 * sem ordem tanto pode ser resultado de blend quanto defeito de importação, e as duas coisas exigem
 * reação oposta. O nome existe para que a distinção não dependa de interpretar um nulo.
 */
public enum BatchOrigin {

    /** Nasceu ao iniciar uma ordem de produção liberada. */
    BREW_ORDER,

    /** Nasceu da execução de um blend: é cerveja que já existia, agora reunida ou separada. */
    BLEND
}
