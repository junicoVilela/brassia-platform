package br.com.brew.brassia.container.application.service;

import java.util.Optional;
import br.com.brew.brassia.container.domain.InspectionPolicy;
import br.com.brew.brassia.container.domain.ContainerKind;
import br.com.brew.brassia.shared.money.Money;
import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.port.outbound.LoanRepository;
import br.com.brew.brassia.container.domain.ContainerLoan;
import br.com.brew.brassia.container.domain.LoanNotAllowedException;
import br.com.brew.brassia.container.domain.SanitationRecord;
import br.com.brew.brassia.container.domain.UnknownContainerException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empréstimo, prazo, caução, perda e higienização (CON-003).
 *
 * <p><strong>Perda encerra o empréstimo E baixa o vasilhame</strong> — e é o único caminho que consegue
 * fazer isso com um keg que está no cliente. A CON-001 recusa a baixa direta de quem está na rua, de
 * propósito: "sumiu" e "descartei" não podem virar a mesma linha no inventário. Aqui a baixa acontece
 * <em>por meio</em> da perda, com motivo, alçada crítica e efeito sobre a caução.
 */
public class LoanHandlers {

    private final LoanRepository loans;
    private final ContainerRepository containers;
    private final ContainerHandlers containerHandlers;

    public LoanHandlers(LoanRepository loans, ContainerRepository containers,
            ContainerHandlers containerHandlers) {
        this.loans = Objects.requireNonNull(loans);
        this.containers = Objects.requireNonNull(containers);
        this.containerHandlers = Objects.requireNonNull(containerHandlers);
    }

    @Transactional
    public UUID lend(UUID breweryId, UUID containerId, UUID customerId, String customerName,
            LocalDate dueOn, Money deposit) {
        var container = containers.find(breweryId, containerId)
                .orElseThrow(() -> new UnknownContainerException(containerId));
        loans.openLoanOf(breweryId, containerId).ifPresent(aberto -> {
            throw LoanNotAllowedException.alreadyLent(container.code());
        });
        var loan = ContainerLoan.open(UUID.randomUUID(), breweryId, containerId, customerId,
                customerName, Instant.now(), dueOn, deposit);
        loans.open(loan);
        return loan.id();
    }

    /**
     * O vasilhame voltou: encerra o empréstimo e a caução passa a ser devida.
     *
     * <p><strong>Não move o contêiner.</strong> Quem move é a coleta (LOG-002) ou a operação manual —
     * duplicar a transição aqui produziria dois caminhos para o mesmo fato, e eles divergiriam na
     * primeira regra nova.
     */
    @Transactional
    public void returned(UUID breweryId, UUID containerId) {
        var loan = loans.openLoanOf(breweryId, containerId)
                .orElseThrow(LoanNotAllowedException::noOpenLoan);
        loan.returned(Instant.now());
        loans.close(loan);
    }

    /**
     * Declara a perda: encerra o empréstimo, retém a caução e <strong>baixa o vasilhame</strong>.
     *
     * <p>A baixa entra aqui porque é o único ponto do sistema em que um keg que está com o cliente pode
     * sair do inventário com motivo — e o motivo é a perda, e não um descarte.
     */
    @Transactional
    public void lost(UUID breweryId, UUID containerId, String reason) {
        var loan = loans.openLoanOf(breweryId, containerId)
                .orElseThrow(LoanNotAllowedException::noOpenLoan);
        loan.lost(Instant.now(), reason);
        loans.close(loan);
        // Baixa por PERDA, e não por descarte: o vasilhame sai do inventário onde quer que estivesse.
        // A CON-001 recusa a baixa comum de quem está na rua justamente para que este seja o único
        // caminho — e ele exige empréstimo aberto, motivo e alçada crítica.
        containerHandlers.declareLost(breweryId, containerId, reason);
    }

    /**
     * O vasilhame perdido reapareceu (DUV-CON-002).
     *
     * <p>Duas coisas acontecem, e nenhuma delas é apagar: o empréstimo ganha o fato novo — com a caução
     * voltando a ser <strong>devida</strong> ao cliente, decisão que o financeiro executa — e o contêiner
     * volta ao inventário como {@code RETURNED}. Sujo, porque passou meses fora de vista: tratá-lo como
     * pronto para encher seria confiar num vasilhame que ninguém olhou.
     */
    @Transactional
    public void recovered(UUID breweryId, UUID containerId, String reason) {
        var loan = loans.lostLoanOf(breweryId, containerId)
                .orElseThrow(LoanNotAllowedException::noLostLoan);
        loan.recovered(Instant.now(), reason);
        loans.close(loan);
        containerHandlers.recover(breweryId, containerId, reason);
    }

    /** A casa define a periodicidade; o sistema nunca a inventa (DUV-CON-001). */
    @Transactional
    public UUID definePolicy(UUID breweryId, ContainerKind kind, int intervalMonths, String note,
            UUID actor) {
        var policy = new InspectionPolicy(UUID.randomUUID(), breweryId, kind, intervalMonths, note,
                actor);
        loans.savePolicy(policy);
        return policy.id();
    }

    @Transactional(readOnly = true)
    public List<InspectionPolicy> policies(UUID breweryId) {
        return loans.policies(breweryId);
    }

    /**
     * A validade sugerida para uma inspeção feita agora, quando há política.
     *
     * <p>Vazio quando a casa não cadastrou: a ausência não afrouxa nada — o vasilhame continua exigindo
     * inspeção válida —, ela só deixa de sugerir.
     */
    @Transactional(readOnly = true)
    public Optional<Instant> suggestValidUntil(UUID breweryId, UUID containerId, Instant performedAt) {
        var container = containers.find(breweryId, containerId)
                .orElseThrow(() -> new UnknownContainerException(containerId));
        return loans.policyOf(breweryId, container.kind())
                .map(p -> p.suggestedValidUntil(performedAt));
    }

    @Transactional
    public UUID sanitize(UUID breweryId, UUID containerId, UUID actor, String method, String note) {
        containers.find(breweryId, containerId)
                .orElseThrow(() -> new UnknownContainerException(containerId));
        var record = new SanitationRecord(UUID.randomUUID(), containerId, Instant.now(), actor,
                method, note);
        loans.record(record);
        return record.id();
    }

    @Transactional(readOnly = true)
    public List<ContainerLoan> openLoans(UUID breweryId, LocalDate overdueOn) {
        return loans.open(breweryId, overdueOn);
    }

    @Transactional(readOnly = true)
    public List<ContainerLoan> historyOf(UUID breweryId, UUID containerId) {
        return loans.ofContainer(breweryId, containerId);
    }

    @Transactional(readOnly = true)
    public List<SanitationRecord> sanitationOf(UUID breweryId, UUID containerId) {
        containers.find(breweryId, containerId)
                .orElseThrow(() -> new UnknownContainerException(containerId));
        return loans.sanitationOf(breweryId, containerId);
    }
}
