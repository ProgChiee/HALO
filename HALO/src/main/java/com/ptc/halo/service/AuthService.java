package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.LoginRequest;
import com.ptc.halo.dtoRequest.StudentRequest;
import com.ptc.halo.dtoResponse.LoginResponse;
import com.ptc.halo.dtoResponse.StudentResponse;
import com.ptc.halo.entity.StudentProfileEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import com.ptc.halo.repository.StudentProfileRepository;
import com.ptc.halo.repository.UserRepository;
import com.ptc.halo.security.JwtService;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
public class AuthService {
    private final AuthAuditService audit;
    @org.springframework.beans.factory.annotation.Autowired
    private LoginTokenIssuer tokenIssuer;

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;


    public AuthService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService, AuthAuditService audit
    ) {

        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService; this.audit = audit;

    }


    @org.springframework.transaction.annotation.Transactional
    public void logout(org.springframework.security.core.Authentication authentication,String bearer) {
        if(authentication==null || !authentication.isAuthenticated()) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required");
        var locked = com.ptc.halo.security.AuthenticatedAccount.lock(userRepository, authentication);
        if (bearer == null || !bearer.startsWith("Bearer ") || !jwtService.isCurrent(bearer.substring(7), new com.ptc.halo.security.CustomUserDetails(locked)))
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid or expired token");
        locked.setTokenVersion(locked.getTokenVersion() + 1);
        userRepository.saveAndFlush(locked);
        audit.logoutUser(locked.getId(),bearer);
    }

    @org.springframework.transaction.annotation.Transactional
    public StudentResponse registerStudent(StudentRequest studentRequest){

        StudentRegistrationValidator.validate(studentRequest);
        var conflicts=new java.util.LinkedHashMap<String,String>();
        if(userRepository.existsByEmail(studentRequest.getEmail())) conflicts.put("email","This email is already registered. Sign in or use password recovery.");
        if(studentProfileRepository.existsByStudentId(studentRequest.getStudentId())) conflicts.put("studentId","This Student ID is already registered. Contact an administrator if you need help.");
        if(!conflicts.isEmpty()) throw new RegistrationValidationException(conflicts,409);

        UserEntity user = new UserEntity();

        user.setName(studentRequest.getName());
        user.setEmail(studentRequest.getEmail());
        user.setPassword(
                passwordEncoder.encode(studentRequest.getPassword())
        );

        user.setRole(Role.STUDENT);
        user.setStatus(Status.ACTIVE);


        UserEntity savedUser =
                userRepository.save(user);



        StudentProfileEntity student =
                new StudentProfileEntity();

        student.setStudentId(studentRequest.getStudentId());
        student.setSection(studentRequest.getSection());
        student.setYearLevel(studentRequest.getYearLevel());
        student.setUser(savedUser);


        StudentProfileEntity savedStudent =
                studentProfileRepository.save(student);



        StudentResponse response =
                new StudentResponse();


        response.setName(savedUser.getName());
        response.setEmail(savedUser.getEmail());
        response.setStudentId(savedStudent.getStudentId());
        response.setSection(savedStudent.getSection());
        response.setYearLevel(savedStudent.getYearLevel());


        return response;

    }



    public LoginResponse login(LoginRequest loginRequest){

        org.springframework.security.core.Authentication authenticated;
        try { authenticated = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );
        } catch (org.springframework.security.authentication.DisabledException disabled) {
            UserEntity disabledUser = userRepository.findByEmail(loginRequest.getEmail()).orElse(null);
            if (disabledUser != null && passwordEncoder.matches(loginRequest.getPassword(), disabledUser.getPassword())) {
                // Missing lifecycle data must never authenticate or enter the
                // enum switch. Do not expose or automatically repair the account.
                if (disabledUser.getStatus() == null) {
                    audit.record(com.ptc.halo.enums.AuthEvent.ACCOUNT_LOGIN_BLOCKED, loginRequest.getEmail());
                    throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
                }
                audit.record(switch(disabledUser.getStatus()) {
                    case INACTIVE -> com.ptc.halo.enums.AuthEvent.ACCOUNT_INACTIVE_LOGIN_ATTEMPT;
                    case ARCHIVED -> com.ptc.halo.enums.AuthEvent.ACCOUNT_ARCHIVED_LOGIN_ATTEMPT;
                    case BLOCKED -> com.ptc.halo.enums.AuthEvent.ACCOUNT_BLOCKED_LOGIN_ATTEMPT;
                    default -> com.ptc.halo.enums.AuthEvent.ACCOUNT_LOGIN_BLOCKED;
                }, loginRequest.getEmail());
                throw new AccountStateException(disabledUser.getStatus() == Status.ARCHIVED);
            }
            audit.record(com.ptc.halo.enums.AuthEvent.LOGIN_FAILED,loginRequest.getEmail());
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        catch (org.springframework.security.core.AuthenticationException failure) {
            audit.record(failure instanceof org.springframework.security.authentication.LockedException ? com.ptc.halo.enums.AuthEvent.ACCOUNT_LOGIN_BLOCKED : com.ptc.halo.enums.AuthEvent.LOGIN_FAILED,loginRequest.getEmail());
            throw failure;
        }
        LoginResponse response;
        try {
            if (authenticated == null || !authenticated.isAuthenticated()
                    || !(authenticated.getPrincipal() instanceof com.ptc.halo.security.CustomUserDetails principal))
                throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
            // Copy verified identity/security state before the fresh transaction loads the account.
            response = tokenIssuer.issue(principal.getUserId(), principal.getPassword(), principal.getTokenVersion(),
                    principal.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        } catch (org.springframework.security.core.AuthenticationException failure) {
            audit.record(com.ptc.halo.enums.AuthEvent.LOGIN_FAILED, loginRequest.getEmail());
            throw failure;
        }
        // Audit runs after the issuance transaction releases its account lock.
        audit.recordUser(com.ptc.halo.enums.AuthEvent.LOGIN_SUCCESS, com.ptc.halo.security.AuthenticatedAccount.principal(authenticated).getUserId());
        return response;
    }
}
