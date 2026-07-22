package br.com.brew.brassia.security.adapter.inbound.web.dto;

/**
 * Corpo possível de {@code POST /login}: sessão estabelecida
 * ({@link SessionResponse}) ou desafio de MFA pendente
 * ({@link MfaRequiredResponse}). Ambos são serializados como estão — a interface
 * apenas dá tipo ao retorno, sem alterar o JSON.
 */
public sealed interface LoginResponse permits SessionResponse, MfaRequiredResponse {}
