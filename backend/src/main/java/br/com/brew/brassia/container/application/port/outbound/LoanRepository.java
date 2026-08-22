package br.com.brew.brassia.container.application.port.outbound;

import br.com.brew.brassia.container.domain.ContainerKind;
import br.com.brew.brassia.container.domain.ContainerLoan;
import br.com.brew.brassia.container.domain.InspectionPolicy;
import br.com.brew.brassia.container.domain.SanitationRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository {

    void open(ContainerLoan loan);

    void close(ContainerLoan loan);

    Optional<ContainerLoan> openLoanOf(UUID breweryId, UUID containerId);

    /** O empréstimo dado como perdido e ainda não recuperado — o que a volta do vasilhame reabre. */
    Optional<ContainerLoan> lostLoanOf(UUID breweryId, UUID containerId);

    Optional<ContainerLoan> find(UUID breweryId, UUID loanId);

    /** Os empréstimos em aberto, opcionalmente só os vencidos até uma data — a fila do dia. */
    List<ContainerLoan> open(UUID breweryId, LocalDate overdueOn);

    List<ContainerLoan> ofContainer(UUID breweryId, UUID containerId);

    /**
     * Grava a política e devolve o id que <strong>sobreviveu</strong>.
     *
     * <p>A gravação é um upsert por cervejaria e tipo: quando já existe política, o id continua sendo o
     * antigo, e o que veio no objeto é descartado. Devolver aqui é o que impede quem chama de anunciar um
     * identificador que não existe (DEB-CON-003 #4).
     */
    UUID savePolicy(InspectionPolicy policy);

    /** A política do tipo, quando a casa cadastrou uma. Vazio é estado legítimo e comum. */
    Optional<InspectionPolicy> policyOf(UUID breweryId, ContainerKind kind);

    List<InspectionPolicy> policies(UUID breweryId);

    void record(SanitationRecord record);

    List<SanitationRecord> sanitationOf(UUID breweryId, UUID containerId);
}
