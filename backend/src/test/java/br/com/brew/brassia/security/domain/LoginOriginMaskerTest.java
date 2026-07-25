package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoginOriginMaskerTest {

    @Test
    void masksIpv4KeepingFirstTwoOctets() {
        assertThat(LoginOriginMasker.maskIp("192.168.10.42")).isEqualTo("192.168.x.x");
    }

    @Test
    void masksIpv6ToFirstTwoGroups() {
        assertThat(LoginOriginMasker.maskIp("2001:db8:1234:5678::1")).isEqualTo("2001:db8::");
    }

    @Test
    void returnsNullForBlankOrUnknownIp() {
        assertThat(LoginOriginMasker.maskIp(null)).isNull();
        assertThat(LoginOriginMasker.maskIp("  ")).isNull();
        assertThat(LoginOriginMasker.maskIp("not-an-ip")).isNull();
    }

    @Test
    void labelsBrowserAndOs() {
        String chromeWindows = "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/120 Safari/537.36";
        assertThat(LoginOriginMasker.browserLabel(chromeWindows)).isEqualTo("Chrome · Windows");

        String firefoxLinux = "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0";
        assertThat(LoginOriginMasker.browserLabel(firefoxLinux)).isEqualTo("Firefox · Linux");
    }

    @Test
    void detectsEdgeBeforeChrome() {
        String edge = "Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537.36 Edg/120";
        assertThat(LoginOriginMasker.browserLabel(edge)).isEqualTo("Edge · Windows");
    }

    @Test
    void returnsNullForBlankUserAgent() {
        assertThat(LoginOriginMasker.browserLabel(null)).isNull();
        assertThat(LoginOriginMasker.browserLabel("")).isNull();
    }
}
