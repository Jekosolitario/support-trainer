package it.zuperman.support_trainer.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;

/**
 * Shared helpers for CSRF + server-side session authentication in MockMvc tests.
 * Continuity uses Spring Session cookies (not servlet {@code MockHttpSession}).
 */
public final class SessionAuthTestSupport {

    private SessionAuthTestSupport() {
    }

    public record CsrfSession(Cookie[] cookies, String token, String headerName) {
        /**
         * Backward-compatible alias used by tests as {@code .with(withSession(csrf))}.
         * Prefer {@link SessionAuthTestSupport#withSession(CsrfSession)}.
         */
        public Cookie[] session() {
            return cookies;
        }
    }

    public static CsrfSession fetchCsrf(MockMvc mockMvc) throws Exception {
        return fetchCsrf(mockMvc, new Cookie[0]);
    }

    public static CsrfSession fetchCsrf(MockMvc mockMvc, CsrfSession existing) throws Exception {
        return fetchCsrf(mockMvc, existing == null ? new Cookie[0] : existing.cookies());
    }

    public static CsrfSession fetchCsrf(MockMvc mockMvc, Cookie[] existingCookies) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (existingCookies != null && existingCookies.length > 0) {
            request.cookie(existingCookies);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        return new CsrfSession(
                mergeCookies(existingCookies, result.getResponse().getCookies()),
                JsonPath.read(body, "$.token"),
                JsonPath.read(body, "$.headerName")
        );
    }

    public static RequestPostProcessor withSession(CsrfSession csrfSession) {
        return request -> {
            if (csrfSession.cookies() != null && csrfSession.cookies().length > 0) {
                request.setCookies(csrfSession.cookies());
            }
            return request;
        };
    }

    public static RequestPostProcessor withCsrf(CsrfSession csrfSession) {
        return request -> {
            request.addHeader(csrfSession.headerName(), csrfSession.token());
            return request;
        };
    }

    public static RequestPostProcessor withSessionAndCsrf(CsrfSession csrfSession) {
        return request -> {
            withSession(csrfSession).postProcessRequest(request);
            withCsrf(csrfSession).postProcessRequest(request);
            return request;
        };
    }

    public static CsrfSession login(
            MockMvc mockMvc,
            String email,
            String password
    ) throws Exception {
        CsrfSession csrf = fetchCsrf(mockMvc);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .with(withSessionAndCsrf(csrf))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isNoContent())
                .andExpect(jsonPath("$").doesNotExist())
                .andReturn();
        Cookie[] cookies = mergeCookies(csrf.cookies(), loginResult.getResponse().getCookies());
        return new CsrfSession(cookies, csrf.token(), csrf.headerName());
    }

    public static CsrfSession loginAndRefreshCsrf(
            MockMvc mockMvc,
            String email,
            String password
    ) throws Exception {
        CsrfSession loggedIn = login(mockMvc, email, password);
        return fetchCsrf(mockMvc, loggedIn.cookies());
    }

    public static String loginBody(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    public static Cookie[] mergeCookies(Cookie[] existing, Cookie[] incoming) {
        Map<String, Cookie> merged = new LinkedHashMap<>();
        if (existing != null) {
            for (Cookie cookie : existing) {
                if (cookie != null && cookie.getName() != null) {
                    merged.put(cookie.getName(), cookie);
                }
            }
        }
        if (incoming != null) {
            for (Cookie cookie : incoming) {
                if (cookie != null && cookie.getName() != null) {
                    if (cookie.getMaxAge() == 0) {
                        merged.remove(cookie.getName());
                    } else {
                        merged.put(cookie.getName(), cookie);
                    }
                }
            }
        }
        return merged.values().toArray(Cookie[]::new);
    }
}
