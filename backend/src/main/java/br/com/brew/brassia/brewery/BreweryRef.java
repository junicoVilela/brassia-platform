package br.com.brew.brassia.brewery;

import java.util.UUID;

/**
 * Referência leve de cervejaria exposta a outros módulos (ex.: contexto de sessão).
 *
 * <p><strong>O fuso entrou por DEB-AI-001.</strong> Ele já existia na tabela desde a primeira migration da
 * cervejaria; o que faltava era atravessar até aqui. Sem ele, quem precisa saber quando o dia ou o mês
 * viram <em>para esta cervejaria</em> tinha de escolher entre o fuso do servidor e uma propriedade por
 * instalação — e as duas erram numa plataforma com cervejarias em fusos diferentes.
 *
 * @param timezone identificador IANA ({@code America/Sao_Paulo}), como gravado na cervejaria
 */
public record BreweryRef(UUID id, String code, String name, String timezone) {}
