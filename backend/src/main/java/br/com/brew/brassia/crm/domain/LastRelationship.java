package br.com.brew.brassia.crm.domain;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * O que conta como "último relacionamento" para o relógio da retenção (DUV-CRM-001).
 *
 * <p><strong>O mais recente entre pedido, entrega e consentimento.</strong> Cada um sozinho erra: só
 * pedido ignora o bar que recebe entrega de um contrato antigo; só entrega ignora quem comprou e ainda não
 * recebeu; só consentimento marcaria como inativo quem nunca precisou reassinar nada.
 *
 * <p><strong>Nenhum deles é "não houve relacionamento".</strong> Um cliente sem pedido, sem entrega e sem
 * consentimento não é um cliente vencido — é um cadastro que nunca foi usado, e tratar a ausência de
 * evidência como evidência de ausência anonimizaria justamente quem acabou de ser cadastrado.
 */
public record LastRelationship(Optional<LocalDate> lastOrder, Optional<LocalDate> lastDelivery,
        Optional<LocalDate> lastConsent) {

    public static LastRelationship of(LocalDate lastOrder, LocalDate lastDelivery,
            LocalDate lastConsent) {
        return new LastRelationship(Optional.ofNullable(lastOrder), Optional.ofNullable(lastDelivery),
                Optional.ofNullable(lastConsent));
    }

    /** A data que o relógio usa. Vazio quando não há relacionamento nenhum registrado. */
    public Optional<LocalDate> latest() {
        return Stream.of(lastOrder, lastDelivery, lastConsent)
                .flatMap(Optional::stream)
                .max(LocalDate::compareTo);
    }

    /**
     * De onde veio a data — para a tela dizer por que aquele contato está na fila.
     *
     * <p>"Vence em março" sem dizer que a conta partiu de uma entrega de 2024 é um número que ninguém
     * consegue conferir, e conferir é o ponto: a anonimização é irreversível.
     */
    public Optional<String> source() {
        return latest().map(data -> {
            if (lastOrder.filter(data::equals).isPresent()) {
                return "último pedido";
            }
            if (lastDelivery.filter(data::equals).isPresent()) {
                return "última entrega";
            }
            return "último consentimento";
        });
    }
}
