package br.com.brew.brassia.container.adapter.inbound.web;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.container.application.service.LoanHandlers;
import br.com.brew.brassia.container.domain.ContainerLoan;
import br.com.brew.brassia.container.domain.DepositAmount;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Empréstimo, prazo, caução, perda e higienização do vasilhame (CON-003). */
@RestController
@RequestMapping("/api/v1/containers")
final class LoanController {

    private final LoanHandlers handlers;
    private final AuditTrail audit;

    LoanController(LoanHandlers handlers, AuditTrail audit) {
        this.handlers = Objects.requireNonNull(handlers);
        this.audit = Objects.requireNonNull(audit);
    }

    /**
     * A fila do dia.
     *
     * <p>Com {@code overdueOn}, só os atrasados. <strong>Atrasado é o que ainda não voltou depois do
     * prazo</strong> — quem devolveu tarde já é história, e misturar os dois faria a cobrança do dia
     * ligar para quem já devolveu.
     */
    @GetMapping("/loans")
    List<LoanView> open(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate overdueOn) {
        principal.requirePermission("container.read");
        var hoje = LocalDate.now(ZoneOffset.UTC);
        return handlers.openLoans(principal.requireBrewery(), overdueOn).stream()
                .map(l -> LoanView.of(l, hoje)).toList();
    }

    @GetMapping("/{id}/loans")
    List<LoanView> history(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id) {
        principal.requirePermission("container.read");
        var hoje = LocalDate.now(ZoneOffset.UTC);
        return handlers.historyOf(principal.requireBrewery(), id).stream()
                .map(l -> LoanView.of(l, hoje)).toList();
    }

    /**
     * O vasilhame sai com prazo e, quando a casa cobra, com caução.
     *
     * <p><strong>Um empréstimo aberto por vasilhame</strong>: o mesmo keg com dois clientes ao mesmo
     * tempo é impossível no mundo e contabilizaria duas cauções.
     */
    @PostMapping("/{id}/loans")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> lend(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody LendRequest request) {
        principal.requirePermission("container.loan.manage");
        var brewery = principal.requireBrewery();
        var loanId = handlers.lend(brewery, id, request.customerId(), request.customerName(),
                request.dueOn(), request.toDeposit());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.loan.lend",
                "container_loan", loanId.toString(), Map.of("dueOn", request.dueOn().toString())));
        return Map.of("id", loanId);
    }

    /** Voltou: a caução passa a ser devida ao cliente — devolvê-la é ato do financeiro. */
    @PostMapping("/{id}/loans/return")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void returned(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id) {
        principal.requirePermission("container.loan.manage");
        var brewery = principal.requireBrewery();
        handlers.returned(brewery, id);
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.loan.return",
                "container", id.toString(), Map.of()));
    }

    /**
     * Declara a perda — alçada crítica.
     *
     * <p>Ela tira um ativo do inventário <strong>e</strong> retém dinheiro do cliente. É também o único
     * caminho pelo qual um keg que está na rua sai do inventário: a CON-001 recusa a baixa direta de
     * propósito, para que "sumiu" e "descartei" não virem a mesma linha.
     */
    @PostMapping("/{id}/loans/loss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void lost(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID id,
            @Valid @RequestBody LossRequest request) {
        principal.requirePermission("container.loan.write_off");
        var brewery = principal.requireBrewery();
        handlers.lost(brewery, id, request.reason());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.loan.loss",
                "container", id.toString(), Map.of("reason", request.reason())));
    }

    /** A higienização, com quem, quando e <strong>o quê</strong>: "higienizado" sozinho é carimbo. */
    @PostMapping("/{id}/sanitations")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> sanitize(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody SanitationRequest request) {
        principal.requirePermission("container.loan.manage");
        var brewery = principal.requireBrewery();
        var recordId = handlers.sanitize(brewery, id, principal.userId(), request.method(),
                request.note());
        audit.record(AuditEvent.success(brewery, principal.userId(), "container.sanitation",
                "container", id.toString(), Map.of("method", request.method())));
        return Map.of("id", recordId);
    }

    @GetMapping("/{id}/sanitations")
    List<SanitationView> sanitations(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID id) {
        principal.requirePermission("container.read");
        return handlers.sanitationOf(principal.requireBrewery(), id).stream()
                .map(r -> new SanitationView(r.id(), r.performedAt(), r.performedBy(), r.method(),
                        r.note()))
                .toList();
    }

    record LendRequest(@NotNull UUID customerId, @NotBlank @Size(max = 160) String customerName,
            @NotNull LocalDate dueOn, BigDecimal depositAmount,
            @Size(min = 3, max = 3) String depositCurrency) {

        DepositAmount toDeposit() {
            // Caução ausente é NULO, e não zero: zero somaria no relatório de valores retidos como se
            // houvesse dinheiro parado.
            return depositAmount == null ? null
                    : new DepositAmount(depositAmount,
                            depositCurrency == null ? "BRL" : depositCurrency);
        }
    }

    record LossRequest(@NotBlank @Size(max = 500) String reason) {}

    record SanitationRequest(@NotBlank @Size(max = 200) String method,
            @Size(max = 500) String note) {}

    /**
     * @param daysLate     zero quando está no prazo — nunca negativo, porque "faltam três dias" é outra
     *                     pergunta e somá-la com atrasos daria zero sem nenhum keg no lugar
     * @param depositOutcome a DECISÃO sobre a caução, e não o dinheiro: o estorno é lançamento
     *                     financeiro, e afirmá-lo aqui seria afirmar um pagamento que ninguém fez
     */
    record LoanView(UUID id, UUID containerId, UUID customerId, String customerName, Instant lentAt,
            LocalDate dueOn, boolean overdue, long daysLate, BigDecimal depositAmount,
            String depositCurrency, String depositOutcome, Instant returnedAt, boolean returnedLate,
            Instant lostAt, String lossReason) {

        static LoanView of(ContainerLoan l, LocalDate today) {
            var caucao = l.deposit().orElse(null);
            return new LoanView(l.id(), l.containerId(), l.customerId(), l.customerName(), l.lentAt(),
                    l.dueOn(), l.overdueOn(today), l.daysLate(today),
                    caucao == null ? null : caucao.amount(),
                    caucao == null ? null : caucao.currency(), l.depositOutcome().name(),
                    l.returnedAt().orElse(null), l.returnedLate(), l.lostAt().orElse(null),
                    l.lossReason().orElse(null));
        }
    }

    record SanitationView(UUID id, Instant performedAt, UUID performedBy, String method,
            String note) {}
}
