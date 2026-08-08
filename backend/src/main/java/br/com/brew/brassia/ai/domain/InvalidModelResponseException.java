package br.com.brew.brassia.ai.domain;

/**
 * A resposta do modelo não satisfez o contrato declarado (AIA-001).
 *
 * <p>Recusa inteira, nunca parcial: aproveitar os campos que vieram certos de uma resposta que veio
 * errada é inventar os que faltaram. É a defesa mais direta contra alucinação que existe neste
 * módulo — um número que o modelo produziu fora de forma não entra no sistema.
 *
 * <p>A mensagem descreve o desvio; o conteúdo recusado não entra nela nem em log, porque documento
 * recuperado e prompt podem carregar dado sensível.
 */
public final class InvalidModelResponseException extends RuntimeException {

    public InvalidModelResponseException(String message) {
        super(message);
    }

    public InvalidModelResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
