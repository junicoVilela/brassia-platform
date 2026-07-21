package br.com.brew.brassia.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    void masksKnownSensitiveKeysAndKeepsOthers() {
        var data = new LinkedHashMap<String, String>();
        data.put("name", "Hoppy Lager");
        data.put("password", "s3cr3t");
        data.put("Authorization", "Bearer abc.def");
        data.put("apiKey", "xyz-123");

        var masked = SensitiveDataMasker.mask(data);

        assertThat(masked.get("name")).isEqualTo("Hoppy Lager");
        assertThat(masked.get("password")).isEqualTo(SensitiveDataMasker.MASK);
        assertThat(masked.get("Authorization")).isEqualTo(SensitiveDataMasker.MASK);
        assertThat(masked.get("apiKey")).isEqualTo(SensitiveDataMasker.MASK);
        assertThat(masked).doesNotContainValue("s3cr3t");
    }
}
