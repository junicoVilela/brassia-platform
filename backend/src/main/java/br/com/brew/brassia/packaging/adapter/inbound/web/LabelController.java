package br.com.brew.brassia.packaging.adapter.inbound.web;

import br.com.brew.brassia.packaging.adapter.inbound.web.dto.LabelDtos;
import br.com.brew.brassia.packaging.application.port.inbound.LabelCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Rótulo e ficha do lote (PKG-004): template versionado, regra regulatória, prévia e impressão. */
@RestController
@RequestMapping("/api/v1/packaging")
final class LabelController {

    private final LabelCommands.SaveTemplate saveTemplate;
    private final LabelCommands.SaveRule saveRule;
    private final LabelCommands.Preview preview;
    private final LabelCommands.Print print;
    private final LabelCommands.Queries queries;

    LabelController(LabelCommands.SaveTemplate saveTemplate, LabelCommands.SaveRule saveRule,
            LabelCommands.Preview preview, LabelCommands.Print print, LabelCommands.Queries queries) {
        this.saveTemplate = saveTemplate;
        this.saveRule = saveRule;
        this.preview = preview;
        this.print = print;
        this.queries = queries;
    }

    @GetMapping("/label-templates")
    List<LabelDtos.TemplateView> templates(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return queries.templates(principal.requireBrewery()).stream().map(LabelDtos.TemplateView::from).toList();
    }

    /** Histórico de versões de um template; o layout de ontem continua consultável. */
    @GetMapping("/label-templates/{code}/versions")
    List<LabelDtos.TemplateView> templateVersions(@PathVariable String code,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return queries.templateVersions(principal.requireBrewery(), code).stream()
                .map(LabelDtos.TemplateView::from)
                .toList();
    }

    /** Salvar cria uma versão nova e preserva a anterior. */
    @PutMapping("/label-templates")
    LabelCommands.SaveTemplate.Result saveTemplate(@Valid @RequestBody LabelDtos.SaveTemplateRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        return saveTemplate.handle(new LabelCommands.SaveTemplate.Command(
                principal.userId(), principal.requireBrewery(), request.code(), request.name(),
                request.fields(), request.note()));
    }

    @GetMapping("/label-rule")
    LabelDtos.RuleView rule(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return queries.rule(principal.requireBrewery())
                .map(LabelDtos.RuleView::from)
                .orElseThrow(() -> new IllegalArgumentException("cervejaria sem regra regulatória de rótulo"));
    }

    /** A obrigatoriedade é lei e vive separada do layout; configurá-la é alçada própria. */
    @PutMapping("/label-rule")
    void saveRule(@Valid @RequestBody LabelDtos.SaveRuleRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.policy.manage");
        saveRule.handle(principal.userId(), principal.requireBrewery(), request.toRule());
    }

    /** Prévia: monta os campos das fontes rastreáveis e acusa o que falta antes da impressão. */
    @GetMapping("/plans/{id}/label/preview")
    LabelDtos.PreviewView preview(@PathVariable UUID id, @RequestParam UUID templateId,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return LabelDtos.PreviewView.from(preview.handle(principal.requireBrewery(), id, templateId));
    }

    @GetMapping("/plans/{id}/label/prints")
    List<LabelDtos.PrintView> prints(@PathVariable UUID id,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.read");
        return queries.prints(principal.requireBrewery(), id).stream().map(LabelDtos.PrintView::from).toList();
    }

    /** Registra a impressão; a partir da segunda o motivo é obrigatório. */
    @PostMapping("/plans/{id}/label/prints")
    LabelCommands.Print.Result print(@PathVariable UUID id,
            @Valid @RequestBody LabelDtos.PrintRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("packaging.plan.manage");
        return print.handle(new LabelCommands.Print.Command(
                principal.userId(), principal.requireBrewery(), id, request.templateId(), request.quantity(),
                request.reason()));
    }
}
