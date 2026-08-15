package br.com.brew.brassia.sales.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A linha do tempo de preço de um produto num canal (SAL-001).
 *
 * <p><strong>A invariante é uma só, e é o motivo desta classe existir: em qualquer dia, no máximo um
 * preço.</strong> Duas vigências sobrepostas fazem "quanto custa hoje?" ter duas respostas, e o sistema
 * escolheria uma pela ordem em que as linhas foram lidas — ou seja, ao acaso. Um pedido sairia com um
 * preço e a fatura com outro, e ninguém conseguiria dizer qual estava certo.
 *
 * <p><strong>O ato comum não é "inserir vigência": é "a partir de tal dia, passa a custar tanto".</strong>
 * Por isso {@link #priceFrom} fecha sozinho o preço aberto anterior, na véspera. Exigir que o operador
 * feche o antigo à mão antes de abrir o novo criaria uma janela em que o produto não tem preço, e o erro
 * apareceria como "produto sem preço" num dia de venda.
 *
 * <p>Sobreposição que <strong>não</strong> é essa — mexer no meio de um período já fechado — é recusada,
 * porque não existe leitura única do que a pessoa quis: encurtar o antigo, dividir em dois, substituir?
 * Adivinhar aqui é reescrever preço histórico por conta própria.
 *
 * <p><strong>Moeda única por linha do tempo.</strong> Trocar de moeda no meio faria a comparação entre
 * dois preços do mesmo produto depender da data, e "aumentou ou baixou?" deixaria de ter resposta.
 */
public final class PriceSchedule {

    private final UUID productId;
    private final UUID channelId;
    private final List<PriceEntry> entries;

    private PriceSchedule(UUID productId, UUID channelId, List<PriceEntry> entries) {
        this.productId = Objects.requireNonNull(productId, "produto");
        this.channelId = Objects.requireNonNull(channelId, "canal");
        this.entries = new ArrayList<>(entries);
    }

    public static PriceSchedule empty(UUID productId, UUID channelId) {
        return new PriceSchedule(productId, channelId, List.of());
    }

    public static PriceSchedule reconstitute(UUID productId, UUID channelId, List<PriceEntry> entries) {
        return new PriceSchedule(productId, channelId, Objects.requireNonNull(entries, "preços"));
    }

    /**
     * "A partir deste dia, passa a custar isto."
     *
     * <p>Fecha o preço em aberto na véspera e acrescenta o novo. Devolve o que mudou, para que o
     * adaptador saiba o que gravar sem reescrever a linha do tempo inteira.
     */
    public Change priceFrom(Money price, boolean taxIncluded, LocalDate from) {
        Objects.requireNonNull(from, "início da vigência");
        var novo = new PriceEntry(price, taxIncluded, from, null);
        requireSameCurrency(price);

        var fechados = entries.stream().filter(e -> !e.isOpenEnded()).toList();
        for (var e : fechados) {
            if (e.overlaps(novo)) {
                throw new OverlappingPriceException(productId, channelId, from);
            }
        }

        var aberto = entries.stream().filter(PriceEntry::isOpenEnded).findFirst();
        Optional<PriceEntry> fechado = Optional.empty();
        if (aberto.isPresent()) {
            var anterior = aberto.get();
            if (!anterior.validFrom().isBefore(from)) {
                // O preço aberto começou hoje ou depois: fechá-lo na véspera criaria uma vigência que
                // termina antes de começar. É o operador corrigindo o que acabou de cadastrar, e a
                // correção certa é apagar o errado, não empilhar outro por cima.
                throw new OverlappingPriceException(productId, channelId, from);
            }
            var encerrado = anterior.closedOnEveOf(from);
            entries.remove(anterior);
            entries.add(encerrado);
            fechado = Optional.of(encerrado);
        }
        entries.add(novo);
        return new Change(novo, fechado.orElse(null));
    }

    /** O que mudou ao aplicar um preço novo: o que entrou, e o que foi encerrado (se algum). */
    public record Change(PriceEntry added, PriceEntry closed) {

        public Optional<PriceEntry> closedEntry() {
            return Optional.ofNullable(closed);
        }
    }

    /**
     * Quanto custava no dia.
     *
     * <p>Vazio é resposta legítima e significa "não havia preço" — o produto existia e ainda não tinha
     * sido precificado, ou o preço só passa a valer depois. Devolver zero faria uma venda sair de graça.
     */
    public Optional<PriceEntry> priceOn(LocalDate day) {
        Objects.requireNonNull(day, "dia");
        return entries.stream().filter(e -> e.coversOn(day)).findFirst();
    }

    /** As vigências em ordem cronológica. Cópia: a linha do tempo não se altera por fora. */
    public List<PriceEntry> entries() {
        return entries.stream().sorted(Comparator.comparing(PriceEntry::validFrom)).toList();
    }

    private void requireSameCurrency(Money price) {
        entries.stream().findFirst().ifPresent(e -> {
            if (!e.price().currency().equals(price.currency())) {
                throw new CurrencyMismatchException(e.price().currency(), price.currency());
            }
        });
    }

    public UUID productId() {
        return productId;
    }

    public UUID channelId() {
        return channelId;
    }
}
