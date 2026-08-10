package br.com.brew.brassia.sensory.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * De onde veio o descritor, e o que a licença dele permite (SEN-002).
 *
 * <p><strong>A licença é estrutura, não observação.</strong> O critério da história pede que "conteúdo
 * licenciado respeite atribuição e nível de permissão" — e um campo de texto dizendo "ver licença" não
 * respeita nada: depende de alguém ler antes de copiar o descritor para um relatório que sai da
 * cervejaria.
 *
 * <p>Modelado assim, a permissão viaja com o dado. Quem exporta um scoresheet sabe, sem consultar
 * ninguém, se pode incluir o limiar e se precisa imprimir a atribuição junto.
 */
public record DescriptorSource(String name, String reference, LicenseTier tier, String attribution) {

    public DescriptorSource {
        name = requireText(name, "name");
        Objects.requireNonNull(tier, "tier");
        // Atribuição obrigatória quando a licença exige. Deixar opcional transformaria a regra da licença
        // em lembrete, e lembrete é o que se esquece na exportação.
        if (tier.requiresAttribution() && (attribution == null || attribution.isBlank())) {
            throw new IllegalArgumentException(
                    "fonte com licença " + tier + " exige texto de atribuição");
        }
    }

    /** Fonte redigida pela própria cervejaria: sem restrição de uso e sem atribuição a terceiro. */
    public static DescriptorSource own(String name) {
        return new DescriptorSource(name, null, LicenseTier.OWN, null);
    }

    /** Nome diferente do acessor do record: um envolve em Optional, o outro devolve o campo cru. */
    public Optional<String> referenceText() {
        return Optional.ofNullable(reference);
    }

    public Optional<String> attributionText() {
        return Optional.ofNullable(attribution);
    }

    /** Se o limiar de percepção pode ser publicado a partir desta fonte. */
    public boolean allowsThreshold() {
        return tier.allowsQuantitativeData();
    }

    private static String requireText(String value, String field) {
        var trimmed = Objects.requireNonNull(value, field).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " não pode ser vazio");
        }
        return trimmed;
    }
}
