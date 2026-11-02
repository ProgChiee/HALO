package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.HealthController;
import com.ptc.halo.security.JwtAuthenticationFilter;
import com.ptc.halo.security.JwtService;
import com.ptc.halo.service.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class HealthHttpTest {
    @Autowired MockMvc mvc;
    @MockBean CustomUserDetailsService users;
    @MockBean JwtService jwt;
    @MockBean ApplicationAvailability availability;

    @Test void anonymousHealthReturnsOnlyPublicStatusWithoutAccountLookup() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        mvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string("{\"status\":\"ok\"}"));
        verifyNoInteractions(users, jwt);
    }

    @Test void healthDoesNotAdvertiseReadinessBeforeStartupOrDuringShutdown() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        mvc.perform(get("/healthz")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string("{\"status\":\"not_ready\"}"));
        verifyNoInteractions(users, jwt);
    }

    @Test void onlyExactGetHealthIsMadePublic() throws Exception {
        for (HttpMethod method : new HttpMethod[]{HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE, HttpMethod.HEAD}) {
            mvc.perform(request(method, "/healthz")).andExpect(status().isUnauthorized());
        }
        for (String path : new String[]{"/healthz/details", "/healthz/", "/api/student/dashboard", "/api/professor/dashboard", "/api/admin/dashboard", "/api/super-admin/dashboard", "/private"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
