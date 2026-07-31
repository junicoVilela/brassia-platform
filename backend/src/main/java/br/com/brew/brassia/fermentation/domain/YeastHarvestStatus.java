package br.com.brew.brassia.fermentation.domain;

/**
 * Estado de uma coleta de levedura (YST-001). Nasce em quarentena; aprovar ou reprovar é
 * decisão humana e terminal — coleta reprovada nunca volta a ficar disponível. Confirmar o
 * uso (YST-002) consome a coleta: ela sai de circulação vinculada ao lote de destino.
 */
public enum YeastHarvestStatus {
    QUARANTINE,
    APPROVED,
    REJECTED,
    USED;

    /** Só levedura aprovada e ainda não usada pode ser reutilizada. */
    public boolean available() {
        return this == APPROVED;
    }
}
