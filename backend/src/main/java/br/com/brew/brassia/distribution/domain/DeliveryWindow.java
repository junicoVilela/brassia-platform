package br.com.brew.brassia.distribution.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A janela combinada com o cliente.
 *
 * <p><strong>Ela é compromisso, e não previsão.</strong> "Entre 8h e 11h" é o que o bar ouviu para
 * decidir quem fica na porta — e é por isso que ela viaja com a parada em vez de ser derivada da ordem da
 * rota: recalcular a janela a partir da sequência faria a promessa mudar toda vez que alguém reordena o
 * roteiro.
 */
public record DeliveryWindow(Instant from, Instant to) {

    public DeliveryWindow {
        Objects.requireNonNull(from, "início da janela");
        Objects.requireNonNull(to, "fim da janela");
        if (!to.isAfter(from)) {
            // Uma janela que fecha antes de abrir é engano de digitação, e o motorista só descobriria
            // com o caminhão carregado.
            throw new IllegalArgumentException("a janela precisa terminar depois de começar");
        }
    }

    public boolean contains(Instant moment) {
        return !moment.isBefore(from) && !moment.isAfter(to);
    }

    /** Se a entrega registrada caiu fora do combinado — o que a LOG-002 precisa saber para explicar. */
    public boolean missedAt(Instant moment) {
        return !contains(moment);
    }
}
