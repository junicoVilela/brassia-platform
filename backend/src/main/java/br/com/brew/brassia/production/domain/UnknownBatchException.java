package br.com.brew.brassia.production.domain;

import java.util.UUID;

/**
 * O lote pedido no endereço não existe <strong>para quem pediu</strong> (DEB-PRD-002).
 *
 * <p>Antes disto a produção respondia {@code 400 bad_request} — "Requisição inválida." —, herdado de um
 * {@code IllegalArgumentException} genérico. O pedido não era inválido: estava bem-formado, e o recurso é
 * que não existe. Quem integra recebia uma resposta mandando conferir o próprio pedido quando o que houve
 * foi outra coisa, e o resto da plataforma já respondia {@code 404 unknown_batch} na mesma situação
 * (custo, relatório, IA).
 *
 * <p><strong>"Não existe" e "é de outra cervejaria" são a mesma resposta, e isso é deliberado.</strong> O
 * repositório filtra por cervejaria, então o lote alheio simplesmente não aparece — e é essa
 * indistinguibilidade que fecha o vazamento. Um código diferente para cada caso confirmaria a existência
 * do lote para quem só tem o identificador.
 */
public final class UnknownBatchException extends RuntimeException {

    private final UUID batchId;

    public UnknownBatchException(UUID batchId) {
        super("lote inexistente: " + batchId);
        this.batchId = batchId;
    }

    public UUID batchId() {
        return batchId;
    }
}
