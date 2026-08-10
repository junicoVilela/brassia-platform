package br.com.brew.brassia.optimization.domain;

/**
 * O que se quer melhorar (OPT-001).
 *
 * <p><strong>Um objetivo por vez, e escolhido por quem pede.</strong> Não há "otimizar tudo": custo,
 * disponibilidade e alvo técnico se contradizem — o malte mais barato muda a cor, o que está em estoque
 * muda o amargor. Um sistema que dissesse otimizar os três entregaria uma média ponderada por pesos que
 * ninguém escolheu, apresentada como ótimo.
 */
public enum Objective {
    /** Menor custo por litro. */
    COST,
    /** Maior uso do que já está em estoque — o que evita comprar e evita perder. */
    AVAILABILITY,
    /** Menor distância às métricas-alvo da receita. */
    TECHNICAL_TARGET
}
