package com.ptc.halo;
import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.StudentRegistryController;
import com.ptc.halo.service.*;
import com.ptc.halo.security.*;
import com.ptc.halo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(StudentRegistryController.class)
@Import({SecurityConfig.class,JwtAuthenticationFilter.class})
class StudentRegistryHttpTest {
 @Autowired MockMvc mvc;@MockBean StudentRegistryService registry;@MockBean StudentRegistryWorkbook workbook;@MockBean UserRepository users;@MockBean JwtService jwt;@MockBean CustomUserDetailsService details;
 void token(String role){when(jwt.extractUserId("synthetic")).thenReturn(1L);when(jwt.isCurrent(eq("synthetic"),any())).thenReturn(true);when(details.loadUserById(1L)).thenReturn(TestIdentity.details(1L,"admin@example.test",role));}
 @Test void registryIsAdminOnlyAndRejectsActingHeaders()throws Exception {
  for(String endpoint:java.util.List.of("","/history","/template")){
   mvc.perform(get("/api/admin/student-registry"+endpoint)).andExpect(status().isUnauthorized());
   for(String role:java.util.List.of("STUDENT","PROFESSOR","SUPER_ADMIN")){token(role);mvc.perform(get("/api/admin/student-registry"+endpoint).header("Authorization","Bearer synthetic")).andExpect(status().isForbidden());}
   token("ADMIN");mvc.perform(get("/api/admin/student-registry"+endpoint).header("Authorization","Bearer synthetic").header("X-Acting-Session","synthetic-mode")).andExpect(status().isForbidden());
  }
  verifyNoInteractions(registry,workbook);
 }
 @Test void adminCanPreviewButConfirmationMustBeExplicit()throws Exception{
  token("ADMIN");var file=new org.springframework.mock.web.MockMultipartFile("file","fixture.xlsx","application/octet-stream",new byte[]{1});
  mvc.perform(multipart("/api/admin/student-registry/preview").file(file).header("Authorization","Bearer synthetic")).andExpect(status().isOk());verify(registry).preview(any());
  mvc.perform(multipart("/api/admin/student-registry/confirm").file(file).param("confirmed","false").header("Authorization","Bearer synthetic")).andExpect(status().isBadRequest());verify(registry,never()).confirm(any(),any());
  for(String role:java.util.List.of("STUDENT","PROFESSOR","SUPER_ADMIN")){token(role);mvc.perform(multipart("/api/admin/student-registry/confirm").file(file).param("confirmed","true").header("Authorization","Bearer synthetic")).andExpect(status().isForbidden());}
 }
}
