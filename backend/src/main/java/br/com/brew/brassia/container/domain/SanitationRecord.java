package br.com.brew.brassia.container.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A higienização do vasilhame (CON-003).
 *
 * <p><strong>É isto que a CON-001 chamava de "alguém diz que está limpo".</strong> Lá, liberar o keg que
 * voltou era um ato explícito sem lastro; aqui o ato ganha nome, data e método. A diferença aparece
 * quando alguém pergunta, três meses depois, se aquele keg foi lavado antes de receber a cerveja que o
 * cliente reclamou — e a resposta precisa ser melhor que "o sistema deixou".
 *
 * @param method o que foi feito, na língua da casa: "soda 2% a 60 °C", "enxágue e vapor". Texto livre de
 *               propósito: padronizar isso é assunto de sanitização (CLN), e inventar uma lista aqui
 *               obrigaria o operador a escolher a opção menos errada
 */
public record SanitationRecord(UUID id, UUID containerId, Instant performedAt, UUID performedBy,
        String method, String note) {

    public SanitationRecord {
        Objects.requireNonNull(id);
        Objects.requireNonNull(containerId, "vasilhame");
        Objects.requireNonNull(performedAt, "quando");
        Objects.requireNonNull(performedBy, "quem higienizou");
        if (method == null || method.isBlank()) {
            // "Higienizado" sem dizer como é um carimbo, e um carimbo não se audita.
            throw new IllegalArgumentException("a higienização precisa dizer o que foi feito");
        }
        method = method.trim();
    }
}
