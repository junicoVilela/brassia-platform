package br.com.brew.brassia.packaging.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prévia do rótulo (PKG-004): o que o layout desenharia, campo a campo, com a origem de cada valor.
 *
 * <p>A prévia <strong>acusa campo ausente antes da impressão</strong>. Falta em campo obrigatório
 * bloqueia a impressão; falta em campo opcional é só aviso. Descobrir depois que um lote inteiro
 * saiu sem a validade custa o lote.
 */
public record LabelPreview(String templateCode, int templateVersion, List<Line> lines,
        List<LabelField> missingRequired, List<LabelField> missingOptional,
        List<LabelField> requiredNotDrawn) {

    /**
     * Um campo do rótulo com o valor resolvido e de onde ele veio.
     *
     * @param source frase que identifica a origem rastreável do valor; nula quando não há fonte
     */
    public record Line(LabelField field, String value, String source, boolean required) {

        public boolean present() {
            return value != null && !value.isBlank();
        }
    }

    public LabelPreview {
        Objects.requireNonNull(templateCode, "templateCode");
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        missingRequired = List.copyOf(Objects.requireNonNull(missingRequired, "missingRequired"));
        missingOptional = List.copyOf(Objects.requireNonNull(missingOptional, "missingOptional"));
        requiredNotDrawn = List.copyOf(Objects.requireNonNull(requiredNotDrawn, "requiredNotDrawn"));
    }

    /** Só imprime quando todo campo obrigatório está desenhado e preenchido. */
    public boolean printable() {
        return missingRequired.isEmpty() && requiredNotDrawn.isEmpty();
    }

    /**
     * Monta a prévia cruzando o layout com a regra e os valores resolvidos das fontes.
     *
     * @param values  valor de cada campo; ausente ou vazio significa "sem fonte disponível"
     * @param sources origem rastreável de cada campo resolvido
     */
    public static LabelPreview of(LabelTemplate template, LabelRegulatoryRule rule,
            Map<LabelField, String> values, Map<LabelField, String> sources) {
        Objects.requireNonNull(template, "template é obrigatório");
        Objects.requireNonNull(rule, "regra é obrigatória");

        var lines = new ArrayList<Line>();
        var missingRequired = new ArrayList<LabelField>();
        var missingOptional = new ArrayList<LabelField>();
        var resolved = new LinkedHashMap<>(Objects.requireNonNull(values, "values"));

        for (var field : template.fields()) {
            var value = resolved.get(field);
            var required = rule.requires(field);
            var line = new Line(field, value, sources.get(field), required);
            lines.add(line);
            if (!line.present()) {
                if (required) {
                    missingRequired.add(field);
                } else {
                    missingOptional.add(field);
                }
            }
        }

        return new LabelPreview(template.code(), template.version(), lines, missingRequired, missingOptional,
                template.missingRequiredFields(rule));
    }
}
