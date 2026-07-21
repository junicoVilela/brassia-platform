package br.com.brew.brassia.security.domain;

/** Ciclo de status explícito da conta interna. */
public enum AccountStatus {
    INVITED,
    ACTIVE,
    LOCKED,
    DISABLED
}
