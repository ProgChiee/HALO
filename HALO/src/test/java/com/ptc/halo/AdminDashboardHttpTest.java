package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.AdminDashboardController;
import com.ptc.halo.dtoResponse.AdminRecentActivityResponse;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminDashboardHttpTest {
    private static final String PATH = "/api/admin/dashboard/recent-activity";
    @Autowired MockMvc mvc;
    @MockBean AdminDashboardService service;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtService jwt;

    void token(String role) {
        when(jwt.extractUsername("test-token")).thenReturn("actor@example.test");
        when(jwt.isCurrent(eq("test-token"), any())).thenReturn(true);
        when(details.loadUserByUsername("actor@example.test")).thenReturn(
                User.withUsername("actor@example.test").password("unused").roles(role).build());
    }

    @Test void adminReceivesActivityArray() throws Exception {
        token("ADMIN");
        var row = new AdminRecentActivityResponse();
        row.setUserName("Admin Actor");
        row.setEmail("actor@example.test");
        when(service.getRecentActivity()).thenReturn(List.of(row));
        mvc.perform(get(PATH).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userName").value("Admin Actor"))
                .andExpect(jsonPath("$[0].email").value("actor@example.test"));
    }

    @Test void unexpectedFailureReturnsSafe500WithoutErrorDispatch() throws Exception {
        token("ADMIN");
        when(service.getRecentActivity()).thenThrow(new IllegalStateException("private SQL details"));
        mvc.perform(get(PATH).header("Authorization", "Bearer test-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                    {"status":500,"code":"ACTIVITY_LOGS_UNAVAILABLE",
                     "message":"Activity logs are temporarily unavailable."}
                    """, true))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(result.getResponse().getErrorMessage()));
    }

    @Test void anonymousStillReceives401() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test void otherRolesRemainForbidden() throws Exception {
        for (String role : List.of("STUDENT", "PROFESSOR", "SUPER_ADMIN")) {
            token(role);
            mvc.perform(get(PATH).header("Authorization", "Bearer test-token"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void serviceAccessDenialIsNotConvertedTo500() throws Exception {
        token("ADMIN");
        when(service.getRecentActivity()).thenThrow(new AccessDeniedException("denied"));
        mvc.perform(get(PATH).header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden());
    }
}
