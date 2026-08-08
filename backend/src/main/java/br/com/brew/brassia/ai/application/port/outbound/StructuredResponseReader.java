package br.com.brew.brassia.ai.application.port.outbound;

/**
 * Lê a resposta do modelo no tipo contratado, ou recusa (AIA-001).
 *
 * <p>Porta porque desserializar é detalhe de biblioteca e o caso de uso não deve conhecer nenhuma:
 * o que ele precisa é da promessa "ou vem no tipo, ou lança".
 */
public interface StructuredResponseReader {

    /**
     * @throws br.com.brew.brassia.ai.domain.InvalidModelResponseException campo faltando, tipo errado,
     *         campo desconhecido, JSON malformado ou invariante do próprio contrato violada
     */
    <T> T read(String json, Class<T> contract);
}
