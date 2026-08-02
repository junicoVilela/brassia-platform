package br.com.brew.brassia.packaging.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Template de rótulo (PKG-004): que campos o layout desenha e em que ordem.
 *
 * <p>É <strong>versionado</strong>: salvar cria uma versão nova e preserva a anterior. Rótulo
 * impresso mês passado precisa continuar explicável — sobrescrever o layout apagaria a única
 * evidência de como aquele rótulo foi montado.
 *
 * <p>O template não decide obrigatoriedade: isso é {@link LabelRegulatoryRule}.
 */
public record LabelTemplate(UUID id, UUID breweryId, String code, String name, int version,
        List<LabelField> fields, String note, UUID createdBy, Instant createdAt) {

    public LabelTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(breweryId, "breweryId");
        code = requireText(code, "código", 40);
        name = requireText(name, "nome", 120);
        if (version < 1) {
            throw new IllegalArgumentException("versão do template começa em 1");
        }
        Objects.requireNonNull(fields, "campos são obrigatórios");
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("template sem campo não desenha rótulo");
        }
        // Campo repetido no layout é erro de montagem, não intenção.
        Set<LabelField> unique = new LinkedHashSet<>(fields);
        if (unique.size() != fields.size()) {
            throw new IllegalArgumentException("campo repetido no template");
        }
        fields = List.copyOf(fields);
        note = note == null || note.isBlank() ? null : requireText(note, "observação", 200);
        Objects.requireNonNull(createdBy, "responsável é obrigatório");
        Objects.requireNonNull(createdAt, "instante da criação é obrigatório");
    }

    public static LabelTemplate firstVersion(UUID breweryId, String code, String name, List<LabelField> fields,
            String note, UUID actorId, Instant at) {
        return new LabelTemplate(UUID.randomUUID(), breweryId, code, name, 1, fields, note, actorId, at);
    }

    /** Nova versão do mesmo template; a anterior permanece consultável. */
    public LabelTemplate nextVersion(String name, List<LabelField> fields, String note, UUID actorId,
            Instant at) {
        return new LabelTemplate(UUID.randomUUID(), breweryId, code, name, version + 1, fields, note, actorId,
                at);
    }

    public boolean draws(LabelField field) {
        return fields.contains(field);
    }

    /** Campos exigidos pela regra que este layout sequer desenha. */
    public List<LabelField> missingRequiredFields(LabelRegulatoryRule rule) {
        return rule.requiredFields().stream().filter(field -> !draws(field)).toList();
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        var trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(field + " excede " + max + " caracteres");
        }
        return trimmed;
    }
}
