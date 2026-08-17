package br.com.brew.brassia.container.adapter.outbound.persistence;

import br.com.brew.brassia.container.application.port.outbound.LoanRepository;
import br.com.brew.brassia.container.domain.ContainerLoan;
import br.com.brew.brassia.shared.money.Money;
import br.com.brew.brassia.container.domain.LoanNotAllowedException;
import br.com.brew.brassia.container.domain.SanitationRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLoanRepository implements LoanRepository {

    private static final String COLUMNS = """
            id, brewery_id, container_id, customer_id, customer_name, lent_at, due_on,
            deposit_amount, deposit_currency, returned_at, lost_at, loss_reason, recovered_at,
            recovery_reason
            """;

    private final JdbcClient jdbc;

    JdbcLoanRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void open(ContainerLoan loan) {
        var caucao = loan.deposit().orElse(null);
        try {
            jdbc.sql("""
                    INSERT INTO container_loan (id, brewery_id, container_id, customer_id,
                            customer_name, lent_at, due_on, deposit_amount, deposit_currency)
                    VALUES (:id, :brewery, :container, :customer, :name, :lentAt, :dueOn, :amount,
                            :currency)
                    """)
                    .param("id", loan.id()).param("brewery", loan.breweryId())
                    .param("container", loan.containerId()).param("customer", loan.customerId())
                    .param("name", loan.customerName())
                    .param("lentAt", Timestamp.from(loan.lentAt())).param("dueOn", loan.dueOn())
                    // Centavos na coluna: `Money` guarda quatro casas para o arredondamento acontecer
                    // uma vez só num total, e caução é valor cobrado do cliente — ele existe em centavos
                    // desde o primeiro dia.
                    .param("amount", caucao == null ? null : caucao.toMinorUnit())
                    .param("currency", caucao == null ? null : caucao.currency())
                    .update();
        } catch (DuplicateKeyException jaEmprestado) {
            // O índice parcial é a garantia: duas telas registrando saídas na mesma manhã passariam por
            // qualquer checagem prévia.
            throw LoanNotAllowedException.alreadyLent(loan.containerId().toString());
        }
    }

    @Override
    public void close(ContainerLoan loan) {
        jdbc.sql("""
                UPDATE container_loan SET returned_at = :returnedAt, lost_at = :lostAt,
                    loss_reason = :reason, recovered_at = :recoveredAt,
                    recovery_reason = :recoveryReason
                WHERE id = :id AND brewery_id = :brewery
                """)
                .param("returnedAt", loan.returnedAt().map(Timestamp::from).orElse(null))
                .param("lostAt", loan.lostAt().map(Timestamp::from).orElse(null))
                .param("reason", loan.lossReason().orElse(null))
                .param("recoveredAt", loan.recoveredAt().map(Timestamp::from).orElse(null))
                .param("recoveryReason", loan.recoveryReason().orElse(null))
                .param("id", loan.id()).param("brewery", loan.breweryId())
                .update();
    }

    /** O empréstimo perdido e ainda não recuperado deste vasilhame — o que a volta reabre. */
    @Override
    public Optional<ContainerLoan> lostLoanOf(UUID breweryId, UUID containerId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM container_loan
                WHERE brewery_id = :brewery AND container_id = :container
                  AND lost_at IS NOT NULL AND recovered_at IS NULL
                ORDER BY lost_at DESC LIMIT 1
                """)
                .param("brewery", breweryId).param("container", containerId)
                .query(JdbcLoanRepository::map).optional();
    }

    @Override
    public Optional<ContainerLoan> openLoanOf(UUID breweryId, UUID containerId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM container_loan
                WHERE brewery_id = :brewery AND container_id = :container
                  AND returned_at IS NULL AND lost_at IS NULL
                """)
                .param("brewery", breweryId).param("container", containerId)
                .query(JdbcLoanRepository::map).optional();
    }

    @Override
    public Optional<ContainerLoan> find(UUID breweryId, UUID loanId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM container_loan WHERE id = :id "
                        + "AND brewery_id = :brewery")
                .param("id", loanId).param("brewery", breweryId)
                .query(JdbcLoanRepository::map).optional();
    }

    @Override
    public List<ContainerLoan> open(UUID breweryId, LocalDate overdueOn) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM container_loan
                WHERE brewery_id = :brewery AND returned_at IS NULL AND lost_at IS NULL
                  AND (CAST(:overdue AS date) IS NULL OR due_on < CAST(:overdue AS date))
                ORDER BY due_on
                """)
                .param("brewery", breweryId).param("overdue", overdueOn)
                .query(JdbcLoanRepository::map).list();
    }

    @Override
    public List<ContainerLoan> ofContainer(UUID breweryId, UUID containerId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM container_loan WHERE brewery_id = :brewery AND container_id = :container
                ORDER BY lent_at DESC
                """)
                .param("brewery", breweryId).param("container", containerId)
                .query(JdbcLoanRepository::map).list();
    }

    @Override
    public void record(SanitationRecord r) {
        jdbc.sql("""
                INSERT INTO container_sanitation (id, container_id, performed_at, performed_by,
                                                  method, note)
                VALUES (:id, :container, :at, :by, :method, :note)
                """)
                .param("id", r.id()).param("container", r.containerId())
                .param("at", Timestamp.from(r.performedAt())).param("by", r.performedBy())
                .param("method", r.method()).param("note", r.note())
                .update();
    }

    @Override
    public List<SanitationRecord> sanitationOf(UUID breweryId, UUID containerId) {
        // O escopo entra pela tabela do contêiner: duplicar brewery_id aqui criaria duas verdades sobre
        // de quem é o vasilhame, e a segunda envelheceria.
        return jdbc.sql("""
                SELECT s.id, s.container_id, s.performed_at, s.performed_by, s.method, s.note
                FROM container_sanitation s
                WHERE s.container_id = :container
                  AND s.container_id IN (SELECT id FROM container WHERE brewery_id = :brewery)
                ORDER BY s.performed_at DESC
                """)
                .param("container", containerId).param("brewery", breweryId)
                .query((rs, row) -> new SanitationRecord(rs.getObject("id", UUID.class),
                        rs.getObject("container_id", UUID.class),
                        rs.getTimestamp("performed_at").toInstant(),
                        rs.getObject("performed_by", UUID.class), rs.getString("method"),
                        rs.getString("note")))
                .list();
    }

    private static ContainerLoan map(ResultSet rs, int row) throws SQLException {
        var amount = rs.getBigDecimal("deposit_amount");
        var returned = rs.getTimestamp("returned_at");
        var lost = rs.getTimestamp("lost_at");
        var recovered = rs.getTimestamp("recovered_at");
        return ContainerLoan.reconstitute(rs.getObject("id", UUID.class),
                rs.getObject("brewery_id", UUID.class), rs.getObject("container_id", UUID.class),
                rs.getObject("customer_id", UUID.class), rs.getString("customer_name"),
                rs.getTimestamp("lent_at").toInstant(), rs.getObject("due_on", LocalDate.class),
                amount == null ? null : new Money(amount, rs.getString("deposit_currency")),
                returned == null ? null : returned.toInstant(),
                lost == null ? null : lost.toInstant(), rs.getString("loss_reason"),
                recovered == null ? null : recovered.toInstant(), rs.getString("recovery_reason"));
    }
}
