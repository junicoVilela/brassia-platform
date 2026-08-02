package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.packaging.application.port.inbound.LabelCommands;
import br.com.brew.brassia.packaging.application.port.outbound.FreshnessRepository;
import br.com.brew.brassia.packaging.application.port.outbound.LabelRepository;
import br.com.brew.brassia.packaging.application.port.outbound.PackagingPlanRepository;
import br.com.brew.brassia.packaging.domain.LabelField;
import br.com.brew.brassia.packaging.domain.LabelNotPrintableException;
import br.com.brew.brassia.packaging.domain.LabelPreview;
import br.com.brew.brassia.packaging.domain.LabelPrint;
import br.com.brew.brassia.packaging.domain.LabelRegulatoryRule;
import br.com.brew.brassia.packaging.domain.LabelTemplate;
import br.com.brew.brassia.packaging.domain.PackagingPlan;
import br.com.brew.brassia.production.BatchLookup;
import br.com.brew.brassia.recipe.RecipeLookup;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Rótulo e ficha do lote (PKG-004).
 *
 * <p>Nada no rótulo é digitado: cada campo é resolvido de uma fonte rastreável — o lote de produção,
 * o plano de envase, a receita publicada, o controle de frescor — e a prévia mostra de onde veio
 * cada valor. Campo sem fonte aparece ausente, e ausente em campo obrigatório barra a impressão.
 */
public final class LabelHandlers {

    private LabelHandlers() {
    }

    public static final class SaveTemplate implements LabelCommands.SaveTemplate {

        private final LabelRepository labels;
        private final AuditTrail audit;

        public SaveTemplate(LabelRepository labels, AuditTrail audit) {
            this.labels = Objects.requireNonNull(labels);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Result handle(Command command) {
            var fields = command.fields() == null
                    ? List.<LabelField>of()
                    : command.fields().stream().map(LabelField::of).toList();
            var at = Instant.now();
            // Salvar acrescenta versão: o rótulo impresso mês passado continua explicável.
            var template = labels.findLatestTemplate(command.breweryId(), command.code())
                    .map(current -> current.nextVersion(command.name(), fields, command.note(),
                            command.actorId(), at))
                    .orElseGet(() -> LabelTemplate.firstVersion(command.breweryId(), command.code(),
                            command.name(), fields, command.note(), command.actorId(), at));
            labels.insertTemplate(template);

            audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "packaging.label.template",
                    "packaging.label-template", template.id().toString(),
                    Map.of("code", template.code(), "version", String.valueOf(template.version()),
                            "fields", String.valueOf(template.fields().size()))));
            return new Result(template.id(), template.version());
        }
    }

    public static final class SaveRule implements LabelCommands.SaveRule {

        private final LabelRepository labels;
        private final AuditTrail audit;

        public SaveRule(LabelRepository labels, AuditTrail audit) {
            this.labels = Objects.requireNonNull(labels);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public void handle(UUID actorId, UUID breweryId, LabelRegulatoryRule rule) {
            labels.saveRule(breweryId, rule);
            audit.record(AuditEvent.success(breweryId, actorId, "packaging.label.rule",
                    "packaging.label-rule", breweryId.toString(),
                    Map.of("requiredFields", String.valueOf(rule.requiredFields().size()))));
        }
    }

    public static final class Preview implements LabelCommands.Preview {

        private final LabelRepository labels;
        private final PackagingPlanRepository plans;
        private final FreshnessRepository freshness;
        private final BatchLookup batches;
        private final RecipeLookup recipes;

        public Preview(LabelRepository labels, PackagingPlanRepository plans, FreshnessRepository freshness,
                BatchLookup batches, RecipeLookup recipes) {
            this.labels = Objects.requireNonNull(labels);
            this.plans = Objects.requireNonNull(plans);
            this.freshness = Objects.requireNonNull(freshness);
            this.batches = Objects.requireNonNull(batches);
            this.recipes = Objects.requireNonNull(recipes);
        }

        @Override
        public LabelPreview handle(UUID breweryId, UUID planId, UUID templateId) {
            var template = template(labels, breweryId, templateId);
            var rule = rule(labels, breweryId);
            var plan = plans.findById(breweryId, planId)
                    .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));
            return assemble(template, rule, plan, freshness, batches, recipes);
        }
    }

    public static final class Print implements LabelCommands.Print {

        private final LabelRepository labels;
        private final PackagingPlanRepository plans;
        private final FreshnessRepository freshness;
        private final BatchLookup batches;
        private final RecipeLookup recipes;
        private final AuditTrail audit;

        public Print(LabelRepository labels, PackagingPlanRepository plans, FreshnessRepository freshness,
                BatchLookup batches, RecipeLookup recipes, AuditTrail audit) {
            this.labels = Objects.requireNonNull(labels);
            this.plans = Objects.requireNonNull(plans);
            this.freshness = Objects.requireNonNull(freshness);
            this.batches = Objects.requireNonNull(batches);
            this.recipes = Objects.requireNonNull(recipes);
            this.audit = Objects.requireNonNull(audit);
        }

        @Override
        public Result handle(Command command) {
            var template = template(labels, command.breweryId(), command.templateId());
            var rule = rule(labels, command.breweryId());
            var plan = plans.findById(command.breweryId(), command.planId())
                    .orElseThrow(() -> new IllegalArgumentException("plano de envase inexistente"));

            // A prévia é a mesma que o operador viu: imprimir não relaxa a checagem.
            var preview = assemble(template, rule, plan, freshness, batches, recipes);
            if (!preview.printable()) {
                throw new LabelNotPrintableException(preview.missingRequired(), preview.requiredNotDrawn());
            }

            // Reimpressão não é escolha de quem chama: é consequência de já existir impressão.
            var reprint = labels.hasPrint(command.breweryId(), command.planId());
            var print = LabelPrint.record(plan.id(), plan.breweryId(), template, command.quantity(), reprint,
                    command.reason(), command.actorId(), Instant.now());
            labels.insertPrint(print);

            audit.record(AuditEvent.success(plan.breweryId(), command.actorId(), "packaging.label.print",
                    "packaging.plan", plan.id().toString(),
                    Map.of("templateCode", print.templateCode(),
                            "templateVersion", String.valueOf(print.templateVersion()),
                            "quantity", String.valueOf(print.quantity()),
                            "reprint", String.valueOf(print.reprint()),
                            "reason", print.reason() == null ? "" : print.reason())));
            return new Result(print.id(), print.reprint(), print.quantity());
        }
    }

    public static final class Queries implements LabelCommands.Queries {

        private final LabelRepository labels;

        public Queries(LabelRepository labels) {
            this.labels = Objects.requireNonNull(labels);
        }

        @Override
        public List<LabelTemplate> templates(UUID breweryId) {
            return labels.findLatestTemplates(breweryId);
        }

        @Override
        public List<LabelTemplate> templateVersions(UUID breweryId, String code) {
            return labels.findTemplateVersions(breweryId, code);
        }

        @Override
        public Optional<LabelRegulatoryRule> rule(UUID breweryId) {
            return labels.findRule(breweryId);
        }

        @Override
        public List<LabelPrint> prints(UUID breweryId, UUID planId) {
            return labels.findPrints(breweryId, planId);
        }
    }

    private static LabelTemplate template(LabelRepository labels, UUID breweryId, UUID templateId) {
        return labels.findTemplate(breweryId, templateId)
                .orElseThrow(() -> new IllegalArgumentException("template de rótulo inexistente"));
    }

    private static LabelRegulatoryRule rule(LabelRepository labels, UUID breweryId) {
        return labels.findRule(breweryId)
                .orElseThrow(() -> new IllegalStateException(
                        "configure a regra regulatória do rótulo antes de gerar rótulos"));
    }

    /**
     * Resolve cada campo da sua fonte rastreável. Campo sem fonte fica vazio de propósito: é a
     * prévia que decide se isso barra a impressão, conforme a regra da casa.
     */
    private static LabelPreview assemble(LabelTemplate template, LabelRegulatoryRule rule, PackagingPlan plan,
            FreshnessRepository freshness, BatchLookup batches, RecipeLookup recipes) {
        var values = new EnumMap<LabelField, String>(LabelField.class);
        var sources = new EnumMap<LabelField, String>(LabelField.class);

        var batch = batches.find(plan.breweryId(), plan.batchId());
        batch.ifPresent(b -> {
            put(values, sources, LabelField.BEER_NAME, b.recipeName(),
                    "lote " + b.code() + " (nome congelado na abertura)");
            put(values, sources, LabelField.BATCH_CODE, b.code(), "lote de produção");
            recipes.findPublishedForOrder(plan.breweryId(), b.recipeId())
                    .flatMap(RecipeLookup.PublishedForOrder::metrics)
                    .map(RecipeLookup.Metrics::abv)
                    .ifPresent(abv -> put(values, sources, LabelField.ABV, abv.toPlainString(),
                            "receita publicada v" + b.recipeVersion() + " (calculado, não medido)"));
        });

        put(values, sources, LabelField.VOLUME_ML, plan.containerVolumeMl().stripTrailingZeros().toPlainString(),
                "embalagem do plano " + plan.code());

        freshness.findByPlan(plan.breweryId(), plan.id())
                .filter(record -> record.effectiveBestBefore() != null)
                .ifPresent(record -> put(values, sources, LabelField.BEST_BEFORE,
                        record.effectiveBestBefore().toString(),
                        record.overridden()
                                ? "controle de frescor — validade sobreposta: " + record.overrideReason()
                                : "controle de frescor — recomendada pela evidência de oxigênio"));

        // QR aponta para a rastreabilidade do lote; sem lote não há o que apontar.
        batch.ifPresent(b -> put(values, sources, LabelField.QR_PAYLOAD,
                "brassia://lote/" + b.code() + "/envase/" + plan.code(),
                "rastreabilidade do lote e do plano de envase"));

        // ALLERGENS fica sem fonte: o catálogo ainda não guarda alergênico (PKG-004-A).
        return LabelPreview.of(template, rule, values, sources);
    }

    private static void put(Map<LabelField, String> values, Map<LabelField, String> sources, LabelField field,
            String value, String source) {
        if (value == null || value.isBlank()) {
            return;
        }
        values.put(field, value);
        sources.put(field, source);
    }
}
