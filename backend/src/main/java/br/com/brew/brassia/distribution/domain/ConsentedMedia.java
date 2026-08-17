package br.com.brew.brassia.distribution.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Assinatura ou foto, <strong>e só quando houve consentimento</strong>.
 *
 * <p>A assinatura de quem recebeu é dado pessoal e a foto da porta do bar pode conter gente. O critério da
 * sprint é explícito: "assinatura/foto quando consentida". Aqui isso não é uma checagem que alguém pode
 * esquecer de fazer — <strong>o objeto não existe sem o consentimento</strong>, então não há caminho no
 * código que guarde a mídia sem ele.
 *
 * <p>Guarda a chave do arquivo, e não o arquivo: o binário vive no armazenamento, e o registro guarda
 * quem consentiu, quando, e para quê.
 *
 * @param purpose para que aquele consentimento vale — "comprovar a entrega", e não "usar como quiser".
 *                Sem finalidade escrita, o consentimento vira cheque em branco
 */
public record ConsentedMedia(MediaKind kind, String storageKey, String consentedByName,
        Instant consentedAt, String purpose) {

    public ConsentedMedia {
        Objects.requireNonNull(kind, "tipo da mídia");
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("a mídia precisa de uma referência de arquivo");
        }
        if (consentedByName == null || consentedByName.isBlank()) {
            // Consentimento sem quem consentiu não é consentimento: é um campo marcado.
            throw new IllegalArgumentException("sem quem consentiu não há consentimento");
        }
        Objects.requireNonNull(consentedAt, "quando consentiu");
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("o consentimento precisa de finalidade");
        }
    }

    public enum MediaKind {
        SIGNATURE,
        PHOTO
    }
}
