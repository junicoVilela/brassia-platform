package br.com.brew.brassia;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {
    @Test
    void modulesMustRespectBoundaries() {
        ApplicationModules.of(BrassiaApplication.class).verify();
    }
}
