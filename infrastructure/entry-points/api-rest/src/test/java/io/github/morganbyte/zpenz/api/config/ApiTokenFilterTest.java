package io.github.morganbyte.zpenz.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ApiTokenFilterTest {

    private static final String TOKEN = "un-token-de-prueba-de-32-caracteres!";

    private final ApiTokenFilter filter = new ApiTokenFilter(TOKEN);

    private MockHttpServletResponse call(String path, String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        if (chain.getRequest() != null) {
            response.setStatus(299); // marca que la petición siguió su camino
        }
        return response;
    }

    @Test
    void letsThroughTheRightToken() throws Exception {
        assertThat(call("/api/v1/transfers", "Bearer " + TOKEN).getStatus()).isEqualTo(299);
    }

    @Test
    void rejectsAMissingOrWrongToken() throws Exception {
        for (String header : new String[]{null, TOKEN, "Bearer ", "Bearer " + TOKEN + "x", "Basic " + TOKEN}) {
            MockHttpServletResponse response = call("/api/v1/accounts", header);
            assertThat(response.getStatus()).as(String.valueOf(header)).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
            assertThat(response.getContentType()).startsWith("application/problem+json");
            assertThat(response.getContentAsString()).doesNotContain(TOKEN);
        }
    }

    @Test
    void onlyTheApiRequiresTheToken() throws Exception {
        assertThat(call("/actuator/health", null).getStatus()).isEqualTo(299);
        assertThat(call("/actuator/info", null).getStatus()).isEqualTo(299);
        assertThat(call("/swagger-ui.html", null).getStatus()).isEqualTo(299);
        assertThat(call("/v3/api-docs", null).getStatus()).isEqualTo(299);
        assertThat(call("/api/v1/cualquier-cosa", null).getStatus()).isEqualTo(401);
        assertThat(call("/api/", null).getStatus()).isEqualTo(401);
    }

    @Test
    void refusesToStartWithAWeakToken() {
        assertThatIllegalStateException().isThrownBy(() -> new ApiTokenFilter("corto"));
        assertThatIllegalStateException().isThrownBy(() -> new ApiTokenFilter(" ".repeat(40)));
    }
}
