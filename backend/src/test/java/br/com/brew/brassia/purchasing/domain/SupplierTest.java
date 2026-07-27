package br.com.brew.brassia.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplierTest {

    private static final UUID BREWERY = UUID.randomUUID();

    @Test
    void registersWithNameAndCode() {
        var s = Supplier.register(BREWERY, "  Maltaria Sul  ", "MSUL", 7);
        assertThat(s.id()).isNotNull();
        assertThat(s.name()).isEqualTo("Maltaria Sul");
        assertThat(s.code()).isEqualTo("MSUL");
        assertThat(s.leadTimeDays()).isEqualTo(7);
        assertThat(s.version()).isEqualTo(1);
    }

    @Test
    void rejectsBlankNameOrCodeOrNegativeLeadTime() {
        assertThatThrownBy(() -> Supplier.register(BREWERY, "  ", "C", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Supplier.register(BREWERY, "Nome", " ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Supplier.register(BREWERY, "Nome", "C", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
