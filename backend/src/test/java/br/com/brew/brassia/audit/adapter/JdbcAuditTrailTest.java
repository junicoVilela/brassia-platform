package br.com.brew.brassia.audit.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.shared.observability.SensitiveDataMasker;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JdbcAuditTrailTest {

    @Test
    void serializesMaskedMetadataWithoutSecrets() {
        var masked = SensitiveDataMasker.mask(Map.of("email", "a@x.com", "password", "segredo123", "token", "abc"));
        var json = JdbcAuditTrail.toJson(masked);

        assertThat(json).contains("a@x.com");
        assertThat(json).doesNotContain("segredo123").doesNotContain("abc");
    }

    @Test
    void emptyMetadataSerializesToEmptyObject() {
        assertThat(JdbcAuditTrail.toJson(Map.of())).isEqualTo("{}");
    }
}
