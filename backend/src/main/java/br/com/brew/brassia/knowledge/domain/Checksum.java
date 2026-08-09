package br.com.brew.brassia.knowledge.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Impressão do texto indexado (RAG-001).
 *
 * <p>Serve para uma pergunta operacional concreta: "este documento que estou indexando é o mesmo que já
 * está lá?" Sem isso, reindexar a mesma FISPQ duas vezes criaria duas fontes idênticas, e a resposta
 * citaria a mesma coisa duas vezes como se fossem duas confirmações independentes.
 *
 * <p>Não é segurança — é identidade de conteúdo. SHA-256 aqui é escolha de robustez contra colisão
 * acidental, não contra adversário.
 */
final class Checksum {

    private Checksum() {
    }

    static String of(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 é exigido de toda implementação da plataforma Java.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", impossible);
        }
    }
}
