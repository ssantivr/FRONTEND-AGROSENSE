package com.agrosense.frontend.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The event service is the only other place pages may connect to, and only its origin. */
class ContentSecurityPolicyTests {

    @Test
    void theEventServicesOriginIsAddedToConnectSrcAndNothingElse() {
        assertThat(SecurityConfig.contentSecurityPolicy("https://events.example:8443/path?x=1"))
                .contains("connect-src 'self' https://events.example:8443;")
                .contains("script-src 'self';");
        assertThat(SecurityConfig.contentSecurityPolicy("")).contains("connect-src 'self';");
    }

    @Test
    void anAddressThatIsNotHttpAddsNothing() {
        for (String url : new String[] {"javascript:alert(1)", "events.example", "ftp://events.example", "http://", "%%"}) {
            assertThat(SecurityConfig.originOf(url)).as(url).isEmpty();
        }
    }
}
