package br.com.brew.brassia.traceability.domain;

import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.util.UUID;

/**
 * Nó inexistente nesta cervejaria (TRC-001).
 *
 * <p>Nenhum provedor soube descrevê-lo. A recusa é deliberada: devolver um grafo vazio para um id
 * que não existe faria "não há elo" e "não há nó" parecerem a mesma resposta — e são opostas.
 * Uma diz que a rastreabilidade tem uma lacuna; a outra, que a pergunta estava errada.
 */
public final class UnknownNodeException extends RuntimeException {

    private final NodeType type;
    private final UUID id;

    public UnknownNodeException(NodeType type, UUID id) {
        super("nó %s %s não existe nesta cervejaria".formatted(type, id));
        this.type = type;
        this.id = id;
    }

    public NodeType type() {
        return type;
    }

    public UUID id() {
        return id;
    }
}
