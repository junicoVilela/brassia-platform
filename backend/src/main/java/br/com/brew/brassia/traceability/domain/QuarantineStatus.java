package br.com.brew.brassia.traceability.domain;

/** Estado da quarentena (FDS-002). Liberar não apaga: a quarentena encerrada continua legível. */
public enum QuarantineStatus {
    OPEN,
    RELEASED
}
