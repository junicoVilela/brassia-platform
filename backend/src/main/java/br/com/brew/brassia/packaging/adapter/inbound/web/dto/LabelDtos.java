package br.com.brew.brassia.packaging.adapter.inbound.web.dto;

import br.com.brew.brassia.packaging.domain.LabelField;
import br.com.brew.brassia.packaging.domain.LabelPreview;
import br.com.brew.brassia.packaging.domain.LabelPrint;
import br.com.brew.brassia.packaging.domain.LabelRegulatoryRule;
import br.com.brew.brassia.packaging.domain.LabelTemplate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** Contratos do rótulo e da ficha do lote (PKG-004). */
public final class LabelDtos {

    private LabelDtos() {
    }

    /** A ordem dos campos é o layout: ela é preservada como veio. */
    public record SaveTemplateRequest(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name,
            @NotEmpty List<String> fields,
            @Size(max = 200) String note) {}

    public record SaveRuleRequest(@NotEmpty List<String> requiredFields) {

        public LabelRegulatoryRule toRule() {
            return new LabelRegulatoryRule(EnumSet.copyOf(requiredFields.stream().map(LabelField::of).toList()));
        }
    }

    public record PrintRequest(
            @NotNull UUID templateId,
            @Positive int quantity,
            @Size(max = 200) String reason) {}

    public record TemplateView(UUID id, String code, String name, int version, List<String> fields, String note,
            Instant createdAt) {

        public static TemplateView from(LabelTemplate t) {
            return new TemplateView(t.id(), t.code(), t.name(), t.version(),
                    t.fields().stream().map(Enum::name).toList(), t.note(), t.createdAt());
        }
    }

    public record RuleView(List<String> requiredFields) {

        public static RuleView from(LabelRegulatoryRule rule) {
            return new RuleView(rule.requiredFields().stream().map(Enum::name).toList());
        }
    }

    /** Cada linha traz o valor e a origem rastreável dele. */
    public record PreviewView(String templateCode, int templateVersion, boolean printable, List<LineView> lines,
            List<String> missingRequired, List<String> missingOptional, List<String> requiredNotDrawn) {

        public record LineView(String field, String value, String source, boolean required, boolean present) {}

        public static PreviewView from(LabelPreview preview) {
            return new PreviewView(preview.templateCode(), preview.templateVersion(), preview.printable(),
                    preview.lines().stream()
                            .map(l -> new LineView(l.field().name(), l.value(), l.source(), l.required(),
                                    l.present()))
                            .toList(),
                    names(preview.missingRequired()), names(preview.missingOptional()),
                    names(preview.requiredNotDrawn()));
        }

        private static List<String> names(List<LabelField> fields) {
            return fields.stream().map(Enum::name).toList();
        }
    }

    public record PrintView(UUID id, String templateCode, int templateVersion, int quantity, boolean reprint,
            String reason, UUID printedBy, Instant printedAt) {

        public static PrintView from(LabelPrint p) {
            return new PrintView(p.id(), p.templateCode(), p.templateVersion(), p.quantity(), p.reprint(),
                    p.reason(), p.printedBy(), p.printedAt());
        }
    }
}
