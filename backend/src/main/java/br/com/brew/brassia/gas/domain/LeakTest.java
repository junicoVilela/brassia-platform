package br.com.brew.brassia.gas.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Teste de vazamento da conexão (GAS-001). É a evidência que libera a linha para servir:
 * método, queda de pressão observada, quem testou e quando. Reprovação exige observação.
 *
 * @param pressureDropBar queda de pressão no intervalo do teste; zero é o resultado esperado
 */
public record LeakTest(boolean passed, String method, BigDecimal pressureDropBar, String note, UUID testedBy,
        Instant testedAt) {

    public LeakTest {
        method = requireText(method, "método do teste", 120);
        Objects.requireNonNull(pressureDropBar, "queda de pressão é obrigatória");
        if (pressureDropBar.signum() < 0) {
            throw new IllegalArgumentException("queda de pressão não pode ser negativa");
        }
        Objects.requireNonNull(testedBy, "responsável pelo teste é obrigatório");
        Objects.requireNonNull(testedAt, "instante do teste é obrigatório");
        if (!passed && (note == null || note.isBlank())) {
            throw new IllegalArgumentException("teste reprovado exige observação");
        }
        note = note == null || note.isBlank() ? null : requireText(note, "observação", 200);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }
}
