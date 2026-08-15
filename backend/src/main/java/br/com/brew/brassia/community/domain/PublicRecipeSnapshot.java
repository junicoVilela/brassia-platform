package br.com.brew.brassia.community.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * A receita como ela sai para fora (COM-001).
 *
 * <p><strong>É uma estrutura própria, construída campo a campo — e essa é a decisão inteira desta
 * história.</strong> A alternativa óbvia seria serializar a {@code Recipe} removendo o que não pode
 * sair. Isso é uma <em>blacklist</em>, e blacklist falha do lado errado: o dia em que alguém acrescentar
 * um campo à receita — um custo estimado, um fornecedor preferencial, uma nota interna —, ele **vaza por
 * padrão**, e ninguém percebe até estar publicado.
 *
 * <p>Aqui é <em>allowlist</em>: o que não está escrito neste arquivo não sai. Um campo novo na
 * {@code Recipe} não aparece aqui até alguém decidir que ele deve. É a mesma razão da
 * {@code WebhookEventType} ser lista fechada.
 *
 * <p><strong>O que NÃO existe aqui, e por quê:</strong>
 * <ul>
 *   <li>{@code breweryId} — é o identificador do inquilino. O plano de testes exige que "busca e feed não
 *       exponham tenant".</li>
 *   <li>{@code ingredientId} — aponta para o catálogo da cervejaria, onde moram <strong>preço de compra e
 *       fornecedor</strong>. O que sai é o nome do ingrediente, que é o que outro cervejeiro precisa.</li>
 *   <li>{@code equipmentId} — identifica o equipamento da casa. O que sai é o volume da brassa, que é o
 *       que permite escalar a receita.</li>
 *   <li>{@code previousRecipeId} — linhagem interna. A linhagem pública é a do fork (COM-003), e é
 *       outra coisa.</li>
 * </ul>
 *
 * <p><strong>É congelado, e não uma vista.</strong> O que foi publicado continua sendo o que foi
 * publicado, mesmo que a receita mude depois. Uma vista faria a edição privada de amanhã alterar em
 * silêncio o que o público já leu — e o autor descobriria que publicou algo que nunca revisou.
 */
public record PublicRecipeSnapshot(String name, String style, BigDecimal batchVolumeLiters,
        Integer boilTimeMinutes, Targets targets, List<Item> items) {

    public PublicRecipeSnapshot {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a receita publicada precisa de nome");
        }
        name = name.strip();
        Objects.requireNonNull(items, "itens");
        items = List.copyOf(items);
    }

    /**
     * Os alvos da receita.
     *
     * <p>Saem porque são o que outro cervejeiro usa para julgar se vale reproduzir. Nenhum deles diz nada
     * sobre a operação da casa: são propriedades da cerveja, não do negócio.
     */
    public record Targets(BigDecimal originalGravity, BigDecimal finalGravity, BigDecimal ibu,
            BigDecimal colorSrm, BigDecimal abvPercent) {}

    /**
     * Um ingrediente, pelo nome.
     *
     * <p><strong>Sem identificador.</strong> O id é a chave do catálogo da cervejaria — quem o tivesse
     * poderia cruzar com qualquer coisa que a plataforma exponha por id no futuro, e a fronteira
     * dependeria de nenhum endpoint novo aceitar aquele identificador. O nome não abre porta nenhuma.
     *
     * <p>Também <strong>sem custo</strong>: o critério da história é literal — "dados de custo, estoque,
     * fornecedor, cliente e produção permanecem privados".
     */
    public record Item(String ingredientName, String stage, BigDecimal quantity, String unit,
            Integer timingMinutes, BigDecimal percentage) {

        public Item {
            if (ingredientName == null || ingredientName.isBlank()) {
                throw new IllegalArgumentException("o item publicado precisa do nome do ingrediente");
            }
            ingredientName = ingredientName.strip();
        }
    }
}
