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


    public void logout(org.springframework.security.core.Authentication authentication,String bearer) {
        if(authentication==null || !authentication.isAuthenticated()) throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("Authentication required");
        audit.logout(authentication.getName(),bearer);
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

        try { authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getEmail(),
                        loginRequest.getPassword()
                )
        );
        } catch (org.springframework.security.authentication.DisabledException disabled) {
            UserEntity disabledUser = userRepository.findByEmail(loginRequest.getEmail()).orElse(null);
            if (disabledUser != null && passwordEncoder.matches(loginRequest.getPassword(), disabledUser.getPassword())) {
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
        UserEntity user =
                userRepository.findByEmail(loginRequest.getEmail())
                        .orElseThrow();



        String token =
                jwtService.generateToken(user);

        LoginResponse response = new LoginResponse();


        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        response.setToken(token);
        response.setMustChangePassword(user.isMustChangePassword());
        audit.record(com.ptc.halo.enums.AuthEvent.LOGIN_SUCCESS,user.getEmail());


        return response;

    }

}