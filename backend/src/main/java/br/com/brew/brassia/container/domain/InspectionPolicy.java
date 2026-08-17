package br.com.brew.brassia.container.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * De quanto em quanto tempo a casa inspeciona cada tipo de vasilhame (DUV-CON-001).
 *
 * <p><strong>A cervejaria cadastra; o sistema não traz número nenhum de fábrica.</strong> A periodicidade
 * de inspeção de vaso de pressão vem de norma e do tipo do contêiner, e escrever aqui "cinco anos" faria o
 * sistema <em>afirmar conformidade que ninguém verificou</em> — num equipamento cuja falha é física. Um
 * prazo errado por excesso é risco; por falta, é frota parada sem motivo. Nos dois casos o sistema teria
 * inventado a resposta.
 *
 * <p><strong>O que ela muda, então.</strong> Antes, quem inspecionava precisava saber de cabeça até quando
 * a inspeção valia e digitar a data. Agora o sistema <em>sugere</em> a data a partir do que a casa
 * cadastrou — e continua aceitando outra, porque a inspeção que encontra um problema pode encurtar o
 * prazo. A sugestão é conveniência, e não autoridade.
 *
 * <p><strong>Sem política, nada muda.</strong> O vasilhame continua exigindo inspeção válida para ser
 * enchido, e a data continua vindo de quem inspeciona. A ausência de política não afrouxa regra nenhuma:
 * ela só deixa de sugerir.
 */
public record InspectionPolicy(UUID id, UUID breweryId, ContainerKind kind, int intervalMonths,
        String note, UUID updatedBy) {

    public InspectionPolicy {
        Objects.requireNonNull(id);
        Objects.requireNonNull(breweryId);
        Objects.requireNonNull(kind, "tipo de vasilhame");
        if (intervalMonths < 1) {
            // Zero seria "inspecionar sempre", que na prática é não ter política — e a ausência já se
            // representa não cadastrando.
            throw new IllegalArgumentException("o intervalo de inspeção deve ser de pelo menos um mês");
        }
    }

    /**
     * A validade sugerida para uma inspeção feita agora.
     *
     * <p>Meses somados à data da inspeção, e não ao dia de hoje: uma inspeção lançada com atraso vale a
     * partir de quando aconteceu, e não de quando alguém digitou.
     */
    public Instant suggestedValidUntil(Instant performedAt) {
        return performedAt.atZone(java.time.ZoneOffset.UTC).plusMonths(intervalMonths).toInstant();
    }

    /** Quanto falta, em meses, entre duas datas — para a tela explicar de onde veio a sugestão. */
    public static long monthsBetween(Instant from, Instant to) {
        return ChronoUnit.MONTHS.between(from.atZone(java.time.ZoneOffset.UTC),
                to.atZone(java.time.ZoneOffset.UTC));
    }
}
