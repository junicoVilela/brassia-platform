package br.com.brew.brassia.ai.domain;

import java.math.BigDecimal;

/**
 * Um número que o domínio calculou, com nome, unidade e origem (AIA-002).
 *
 * <p><strong>Por que os fatos existem como tipo.</strong> A avaliação de um lote é cheia de número —
 * volume, perda, ABV, custo por litro — e nenhum deles pode vir do modelo. O domínio calcula, o fato
 * carrega o resultado com um identificador, e o modelo recebe a lista e explica o risco referenciando-a.
 * Sem essa inversão, "a perda foi de 12%" seria uma afirmação do modelo; com ela, é uma afirmação do
 * sistema que o modelo apenas interpretou.
 *
 * <p><strong>{@code source} não é decoração.</strong> Ela nomeia o serviço que produziu o número, e é o que
 * torna verificável o critério da história: "cálculos referenciam serviço de domínio" deixa de ser promessa
 * e passa a ser um campo que viaja até a tela.
 *
 * <p><strong>O identificador não tem dígito, de propósito.</strong> A conferência varre o texto do modelo
 * procurando número que não seja fato; se o identificador tivesse dígito e o modelo o escrevesse na prosa,
 * a varredura o leria como número inventado. Identificador só de letras e sublinhado elimina a ambiguidade.
 *
 * @param id     identificador estável, só letras e sublinhado — é por ele que o modelo cita
 * @param label  como o número se chama em português, para o modelo e para a tela
 * @param value  o valor calculado; nulo quando o fato é ausência ("não houve transferência")
 * @param unit   unidade explícita; vazia quando o número é contagem
 * @param source o serviço de domínio que calculou — {@code production}, {@code recipe}, {@code costing}…
 */
public record Fact(String id, String label, BigDecimal value, String unit, String source) {

    public Fact {
        id = requireText(id, "id");
        if (!id.matches("[a-z_]+")) {
            throw new IllegalArgumentException(
                    "id de fato deve ter apenas letras minúsculas e sublinhado: " + id);
        }
        label = requireText(label, "label");
        source = requireText(source, "source");
        unit = unit == null ? "" : unit.strip();
    }

    public static Fact of(String id, String label, BigDecimal value, String unit, String source) {
        return new Fact(id, label, value, unit, source);
    }

    /** Contagem: número sem unidade, como "3 desvios". */
    public static Fact count(String id, String label, long value, String source) {
        return new Fact(id, label, BigDecimal.valueOf(value), "", source);
    }

    /**
     * Fato de ausência: o número não existe, e isso é informação.
     *
     * <p>"Não houve transferência" não é "transferiu zero litro", e "ninguém mediu" não é "todas as
     * medições ficaram na faixa". Um zero no lugar da ausência faria o modelo interpretar um lote parado
     * como um lote perfeito.
     */
    public static Fact absent(String id, String label, String source) {
        return new Fact(id, label, null, "", source);
    }

    public boolean known() {
        return value != null;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.strip();
    }
}
