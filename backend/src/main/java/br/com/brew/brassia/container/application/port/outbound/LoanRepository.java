package br.com.brew.brassia.container.application.port.outbound;

import br.com.brew.brassia.container.domain.ContainerLoan;
import br.com.brew.brassia.container.domain.SanitationRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoanRepository {

    void open(ContainerLoan loan);

    void close(ContainerLoan loan);

    Optional<ContainerLoan> openLoanOf(UUID breweryId, UUID containerId);

    Optional<ContainerLoan> find(UUID breweryId, UUID loanId);

    /** Os empréstimos em aberto, opcionalmente só os vencidos até uma data — a fila do dia. */
    List<ContainerLoan> open(UUID breweryId, LocalDate overdueOn);

    List<ContainerLoan> ofContainer(UUID breweryId, UUID containerId);

    void record(SanitationRecord record);

    List<SanitationRecord> sanitationOf(UUID breweryId, UUID containerId);
}
