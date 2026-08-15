package br.com.brew.brassia.sales.adapter.outbound.query;

import br.com.brew.brassia.sales.OrderHistoryLookup;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class JdbcOrderHistoryLookup implements OrderHistoryLookup {

    private final JdbcClient jdbc;

    JdbcOrderHistoryLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MonthlyDemand> monthlyDemand(UUID breweryId, UUID productId, YearMonth from,
            YearMonth to) {
        var porMes = new HashMap<YearMonth, BigDecimal>();
        jdbc.sql("""
                SELECT date_trunc('month', o.placed_on)::date AS mes, SUM(l.quantity) AS unidades
                FROM sales_order o
                JOIN sales_order_line l ON l.order_id = o.id AND l.brewery_id = o.brewery_id
                WHERE o.brewery_id = :brewery AND l.product_id = :product
                  AND o.status <> 'CANCELLED'
                  AND o.placed_on >= :from AND o.placed_on < :to
                GROUP BY 1
                """)
                .param("brewery", breweryId).param("product", productId)
                .param("from", Date.valueOf(from.atDay(1)))
                .param("to", Date.valueOf(to.plusMonths(1).atDay(1)))
                .query((rs, row) -> Map.entry(YearMonth.from(rs.getDate("mes").toLocalDate()),
                        rs.getBigDecimal("unidades")))
                .list()
                .forEach(e -> porMes.put(e.getKey(), e.getValue()));

        // Preenche os meses vazios com zero. Omiti-los encurtaria a série e faria a média subir — a
        // previsão passaria a descrever só os meses bons, que é o erro mais fácil de cometer aqui e o
        // mais difícil de perceber depois.
        var out = new ArrayList<MonthlyDemand>();
        for (var m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            out.add(new MonthlyDemand(m, porMes.getOrDefault(m, BigDecimal.ZERO)));
        }
        return out;
    }
}
