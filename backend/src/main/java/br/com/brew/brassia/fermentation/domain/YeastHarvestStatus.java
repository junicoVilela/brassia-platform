package br.com.brew.brassia.fermentation.domain;

/**
 * Estado de uma coleta de levedura (YST-001). Nasce em quarentena; aprovar ou reprovar é
 * decisão humana e terminal — coleta reprovada nunca volta a ficar disponível.
 */
public enum YeastHarvestStatus {
    QUARANTINE,
    APPROVED,
    REJECTED;

    /** Só levedura aprovada pode ser reutilizada. */
    public boolean available() {
        return this == APPROVED;
    }
}
