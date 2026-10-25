package com.ptc.halo;

import com.ptc.halo.controller.*;
import com.ptc.halo.dtoRequest.AdminActingSessionRequest;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticatedIdentityRegressionTest {
    UserRepository users;
    Map<Long,UserEntity> accounts;
    UserEntity original;
    JwtService jwt;

    UserEntity account(long id, Role role, String email) {
        var u=new UserEntity();u.setId(id);u.setRole(role);u.setStatus(Status.ACTIVE);
        u.setEmail(email);u.setName("Account "+id);u.setPassword("old-hash");return u;
    }
    @BeforeEach void setup() {
        users=mock(UserRepository.class);accounts=new java.util.concurrent.ConcurrentHashMap<>();
        when(users.findById(anyLong())).thenAnswer(c->Optional.ofNullable(accounts.get(c.getArgument(0))));
        when(users.findForAssessmentLifecycle(anyLong())).thenAnswer(c->Optional.ofNullable(accounts.get(c.getArgument(0))));
        jwt=new JwtService(Base64.getEncoder().encodeToString(new byte[32]));
    }
    // Deterministic scheduling point: email reuse commits after JWT validation,
    // before downstream controller/service identity resolution. No live database.
    void afterAuthentication(Role role, Consumer<Authentication> downstream) throws Exception {
        original=account(1,role,"before@example.test");accounts.put(1L,original);
        var filter=new JwtAuthenticationFilter(jwt,new CustomUserDetailsService(users));
        var request=new MockHttpServletRequest("GET","/api/profile");
        request.addHeader("Authorization","Bearer "+jwt.generateToken(original));
        var validated=new java.util.concurrent.CountDownLatch(1);
        var reused=new java.util.concurrent.CountDownLatch(1);
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        var requestTask=executor.submit(()->{
            try {
                filter.doFilter(request,new MockHttpServletResponse(),(req,res)->{
                    validated.countDown();
                    try {assertTrue(reused.await(10,java.util.concurrent.TimeUnit.SECONDS));}
                    catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
                    downstream.accept(SecurityContextHolder.getContext().getAuthentication());
                });
            } finally {SecurityContextHolder.clearContext();}
            return null;
        });
        try {
            assertTrue(validated.await(10,java.util.concurrent.TimeUnit.SECONDS));
            accounts.put(1L,account(1,role,"changed@example.test"));
            accounts.put(2L,account(2,role,"before@example.test"));
            reused.countDown();
            requestTask.get(15,java.util.concurrent.TimeUnit.SECONDS);
        } finally {reused.countDown();executor.shutdownNow();}
        verify(users,never()).findByEmail(anyString());
    }
    @ParameterizedTest @EnumSource(Role.class)
    void profileNameAndPhotoStayWithOriginalAccount(Role role) throws Exception {
        var audit=mock(ActivityLogService.class);var photos=mock(ProfilePhotoService.class);
        var profile=new SelfProfileService(users,audit);
        var photo=new ProfilePhotoController(photos,users,mock(AdminStudentModeService.class),mock(AdminStudentPreviewService.class),mock(AdminProfessorModeService.class));
        when(users.updateOwnName(1L,"Updated Name")).thenReturn(1);
        afterAuthentication(role,auth->{
            assertEquals(1L,AuthenticatedAccount.find(users,auth).orElseThrow().getId());
            assertEquals(1L,profile.updateName(auth,"Updated Name").userId());
            try {
                photo.photo(auth);
                photo.save(auth,new MockMultipartFile("photo","photo.png","image/png",new byte[]{1}),null);
            } catch(Exception e){throw new AssertionError(e);}
        });
        verify(users).updateOwnName(1L,"Updated Name");verify(users,never()).updateOwnName(eq(2L),anyString());
        verify(photos).read(1L);verify(photos).save(eq(1L),any());verify(photos,never()).read(2L);
        verify(audit).createLog(argThat(u->u.getId()==1L),eq(ActivityType.ACCOUNT),anyString());
    }
    @Test void adminProfileAndActingAuditKeepOriginalActor() throws Exception {
        var audit=mock(ActivityLogService.class);var sessions=mock(AdminActingSessionRepository.class);
        when(sessions.save(any())).thenAnswer(c->c.getArgument(0));
        var mode=new AdminActingSessionService(users,sessions,audit);
        var controller=new AdminController(mock(AdminService.class),users,audit,new ProfileService(mock(StudentProfileRepository.class),mock(ProfessorRepository.class)));
        accounts.put(3L,account(3,Role.PROFESSOR,"prof@example.test"));
        afterAuthentication(Role.ADMIN,auth->{
            assertEquals(1L,controller.getProfile(auth).getBody().getUserId());
            var created=mode.create(auth,new AdminActingSessionRequest(3L,Role.PROFESSOR));
            assertEquals(1L,created.adminUserId());
            assertThrows(AdminApiException.class,()->mode.validate(auth,"another-admin-session"));
        });
        verify(sessions).findByIdAndAdmin_Id("another-admin-session",1L);
        verify(audit).createActingLog(argThat(s->s.getAdmin().getId()==1L),eq("Started acting session"));
    }
    @ParameterizedTest @EnumSource(Role.class)
    void logoutRevokesOriginalAccountOnlyAfterEmailReuse(Role role) throws Exception {
        var audit=mock(AuthAuditService.class);
        var service=new AuthService(users,mock(StudentProfileRepository.class),mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(org.springframework.security.authentication.AuthenticationManager.class),jwt,audit);
        afterAuthentication(role,auth->service.logout(auth,"Bearer "+jwt.generateToken(original)));
        assertEquals(1,accounts.get(1L).getTokenVersion());assertEquals(0,accounts.get(2L).getTokenVersion());
        verify(audit).logoutUser(eq(1L),anyString());
    }
    @ParameterizedTest @EnumSource(Role.class)
    void passwordChangeLocksOriginalIdRatherThanOldEmail(Role role) throws Exception {
        var encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(encoder.matches("current-password","old-hash")).thenReturn(true);
        when(encoder.encode("New-password123")).thenReturn("new-hash");
        var service=new PasswordResetService(mock(PasswordResetOtpRepository.class),users,mock(EmailService.class),encoder,
                mock(AuthAuditService.class),mock(PasswordResetProtection.class),mock(PasswordResetThrottle.class),mock(org.springframework.transaction.PlatformTransactionManager.class));
        afterAuthentication(role,auth->service.changePassword(original,"current-password","New-password123"));
        assertEquals("new-hash",accounts.get(1L).getPassword());assertEquals(1,accounts.get(1L).getTokenVersion());
        assertEquals("old-hash",accounts.get(2L).getPassword());assertEquals(0,accounts.get(2L).getTokenVersion());
        verify(users).findForAssessmentLifecycle(1L);verify(users,never()).findForPasswordChange(anyString());
    }
    enum Change { DELETE, INACTIVE, ARCHIVED, BLOCKED, NULL_STATUS, ROLE, REVOKE }
    @ParameterizedTest @EnumSource(Change.class)
    void securityChangesAfterJwtValidationFailClosed(Change change) throws Exception {
        afterAuthentication(Role.ADMIN,auth->{
            var current=accounts.get(1L);
            switch(change){case DELETE->accounts.remove(1L);case INACTIVE->current.setStatus(Status.INACTIVE);
                case ARCHIVED->current.setStatus(Status.ARCHIVED);case BLOCKED->current.setStatus(Status.BLOCKED);
                case NULL_STATUS->current.setStatus(null);case ROLE->current.setRole(Role.SUPER_ADMIN);case REVOKE->current.setTokenVersion(1);}
            var service=new SelfProfileService(users,mock(ActivityLogService.class));
            if(change==Change.DELETE || change==Change.REVOKE)
                assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->service.updateName(auth,"No Update"));
            else
                assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.updateName(auth,"No Update"));
            verify(users,never()).updateOwnName(anyLong(),anyString());
        });
    }
    @ParameterizedTest @EnumSource(value=Role.class,names={"STUDENT","PROFESSOR","SUPER_ADMIN"})
    void otherRolesCannotAcquireAdminActingIdentity(Role role) throws Exception {
        var sessions=mock(AdminActingSessionRepository.class);
        afterAuthentication(role,auth->{
            var mode=new AdminActingSessionService(users,sessions,mock(ActivityLogService.class));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->mode.create(auth,new AdminActingSessionRequest(3L,Role.PROFESSOR)));
        });
        verifyNoInteractions(sessions);
    }
    @Test void emailOnlyPrincipalIsNeverAccepted() {
        var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated("before@example.test",null,List.of());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->AuthenticatedAccount.find(users,auth));
        verifyNoInteractions(users);
    }
    @Test void principalVersionIsSnapshotNotMutableEntityVersion() {
        var u=account(1,Role.ADMIN,"admin@example.test");var auth=TestIdentity.auth(u);accounts.put(1L,u);
        u.setTokenVersion(1);
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->AuthenticatedAccount.find(users,auth));
    }
}
