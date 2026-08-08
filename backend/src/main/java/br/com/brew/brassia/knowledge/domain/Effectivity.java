package br.com.brew.brassia.knowledge.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * De quando até quando um documento vale (RAG-001).
 *
 * <p><strong>Vigência não é data de upload.</strong> Um laudo assinado em março pode valer a partir de
 * abril, e uma FISPQ substituída em junho continua sendo o documento que valia em maio — que é
 * exatamente o que importa quando se investiga um lote produzido em maio. Guardar só "quando entrou no
 * sistema" perderia a única informação que responde a essa pergunta.
 *
 * <p>Fim aberto ({@code null}) é o caso normal: vale até que algo o substitua. Não é "vigência
 * desconhecida" — é "ainda vigente", e a diferença é o que permite responder sem hesitar sobre hoje.
 *
 * <p>Datas locais, e não instantes, porque vigência de documento é decidida em dia de calendário: um
 * laudo vale "a partir de 1º de abril", não "a partir de 1º de abril às 03:00 UTC".
 */
public record Effectivity(LocalDate from, LocalDate to) {

    public Effectivity {
        Objects.requireNonNull(from, "início da vigência é obrigatório");
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("o fim da vigência é anterior ao início");
        }
    }

    /** Vigência aberta: vale a partir da data, até que algo a encerre. */
    public static Effectivity from(LocalDate from) {
        return new Effectivity(from, null);
    }

    public boolean coversDate(LocalDate date) {
        Objects.requireNonNull(date, "data");
        if (date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    public boolean open() {
        return to == null;
    }

    /**
     * Encerra a vigência no dia anterior ao início do que substitui.
     *
     * <p>Dia anterior, e não o mesmo dia: dois documentos vigentes no mesmo dia sobre o mesmo assunto
     * fariam a recuperação devolver duas respostas conflitantes sem meio de escolher.
     */
    public Effectivity endedBefore(LocalDate replacementFrom) {
        Objects.requireNonNull(replacementFrom, "início do substituto");
        var lastDay = replacementFrom.minusDays(1);
        if (lastDay.isBefore(from)) {
            // O substituto começa no mesmo dia ou antes: a vigência deste vira um único dia, o seu
            // próprio início. Encolher para trás do início produziria um intervalo impossível.
            return new Effectivity(from, from);
        }
        return new Effectivity(from, lastDay);
    }
}
