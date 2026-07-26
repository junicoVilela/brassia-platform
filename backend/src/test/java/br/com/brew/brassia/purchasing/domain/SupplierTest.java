package br.com.brew.brassia.purchasing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplierTest {

    private static final UUID BREWERY = UUID.randomUUID();

    @Test
    void registersWithNameAndCode() {
        var s = Supplier.register(BREWERY, "  Maltaria Sul  ", "MSUL");
        assertThat(s.id()).isNotNull();
        assertThat(s.name()).isEqualTo("Maltaria Sul");
        assertThat(s.code()).isEqualTo("MSUL");
        assertThat(s.version()).isEqualTo(1);
    }

    @Test
    void rejectsBlankNameOrCode() {
        assertThatThrownBy(() -> Supplier.register(BREWERY, "  ", "C"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Supplier.register(BREWERY, "Nome", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
