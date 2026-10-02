# Full corrected LessonChat source files

See README.md for deployment and verification.

## backend/RunRegression.java

```java
import java.lang.reflect.*;
import org.junit.jupiter.api.*;
import org.springframework.test.context.TestContextManager;
public class RunRegression {
 public static void main(String[] args) throws Exception {
  int passed=0, failed=0;
  for(String name: args){
   Class<?> c=Class.forName(name);
   boolean spring=name.endsWith("HttpTest");
   TestContextManager context=spring?new TestContextManager(c):null;
   if(spring)context.beforeTestClass();
   for(Method test:c.getDeclaredMethods())if(test.isAnnotationPresent(Test.class)){
    Constructor<?> ctor=c.getDeclaredConstructor();ctor.setAccessible(true);Object instance=ctor.newInstance();
    Throwable problem=null;
    try {
     if(spring){context.prepareTestInstance(instance);context.beforeTestMethod(instance,test);context.beforeTestExecution(instance,test);}
     for(Method setup:c.getDeclaredMethods())if(setup.isAnnotationPresent(BeforeEach.class)){setup.setAccessible(true);setup.invoke(instance);}
     test.setAccessible(true);test.invoke(instance);passed++;System.out.println("PASS "+test.getName());
    }catch(Throwable e){problem=e instanceof InvocationTargetException?e.getCause():e;failed++;problem.printStackTrace();}
    finally{if(spring){context.afterTestExecution(instance,test,problem);context.afterTestMethod(instance,test,problem);}}
   }
   if(spring)context.afterTestClass();
  }
  System.out.println("RESULT passed="+passed+" failed="+failed);if(failed>0)System.exit(1);
 }
}
```

## backend/src/main/java/com/ptc/halo/config/SecurityConfig.java

```java
package com.ptc.halo.config;

import com.ptc.halo.security.JwtAuthenticationFilter;
import com.ptc.halo.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import com.ptc.halo.security.ApiSecurityErrors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(CustomUserDetailsService customUserDetailsService, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.customUserDetailsService = customUserDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }
    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());


        return provider;
    }
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {

        return configuration.getAuthenticationManager();
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource()
                ))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authenticationProvider(authenticationProvider())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                )

                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                ApiSecurityErrors.write(request, response, 401, "AUTHENTICATION_REQUIRED"))
                        .accessDeniedHandler((request, response, exception) ->
                                ApiSecurityErrors.write(request, response, 403, "INVALID_ROLE_OR_ACCESS")))
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register/student",
                                "/api/auth/forgot-password",
                                "/api/auth/forgot-password/resend",
                                "/api/auth/reset-password"
                        ).permitAll()

                        .requestMatchers("/api/super-admin/**")
                        .hasRole("SUPER_ADMIN")

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/professor/**")
                        .hasRole("PROFESSOR")

                        .requestMatchers("/api/student/**")
                        .hasRole("STUDENT")



                        .anyRequest().authenticated()
                );

        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOriginPatterns(
                List.of(
                        "http://localhost:*",
                        "http://127.0.0.1:*"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        configuration.setAllowCredentials(true);


        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}
```

## backend/src/main/java/com/ptc/halo/controller/ApiMethodExceptionHandler.java

```java
package com.ptc.halo.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Method mismatches happen before Spring can select a controller.
@RestControllerAdvice
public class ApiMethodExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiMethodExceptionHandler.class);
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException error, HttpServletRequest request) {
        log.warn("request_rejected method={} path={} status=405 reason=METHOD_NOT_ALLOWED",
                request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(405).header("Allow", String.join(", ", error.getSupportedMethods() == null ? new String[0] : error.getSupportedMethods()))
                .body(Map.of("status", 405, "code", "METHOD_NOT_ALLOWED", "message", "Use the supported HTTP method."));
    }
}

```

## backend/src/main/java/com/ptc/halo/controller/StudentAiLearningController.java

```java
package com.ptc.halo.controller;

import com.ptc.halo.dtoResponse.AiLearningModuleResponse;
import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.enums.LessonStatus;
import com.ptc.halo.repository.AiLearningModuleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ptc.halo.service.StudentLessonAccessService;
import com.ptc.halo.service.StudentLearningProgressionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@PreAuthorize("hasRole('STUDENT')")
@RequestMapping("/api/student/ai-learning-modules")
public class StudentAiLearningController {
    private final StudentLessonAccessService access;
    private final StudentLearningProgressionService progression;

    private final AiLearningModuleRepository aiLearningModuleRepository;

    public StudentAiLearningController(
            AiLearningModuleRepository aiLearningModuleRepository, StudentLessonAccessService access, StudentLearningProgressionService progression) {
        this.access = access;
        this.progression = progression;

        this.aiLearningModuleRepository =
                aiLearningModuleRepository;
    }

    @GetMapping("/week/{weekId}")
    public ResponseEntity<AiLearningModuleResponse> getApprovedLesson(
            @PathVariable("weekId") Long weekId, Authentication authentication) {

        AiLearningModuleEntity module =
                aiLearningModuleRepository
                        .findByWeekId(weekId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND")
                        );

        progression.validateModuleAccess(module.getId(), access.requireStudent(authentication));

        return ResponseEntity.ok(
                convertToResponse(module)
        );
    }

    private AiLearningModuleResponse convertToResponse(
            AiLearningModuleEntity module) {

        AiLearningModuleResponse response =
                new AiLearningModuleResponse();

        response.setId(module.getId());
        response.setWeekId(module.getWeek().getId());

        response.setYoutubeLink(
                module.getYoutubeLink()
        );

        response.setLessonText(
                module.getLessonText()
        );

        response.setGeneratedObjectives(
                module.getGeneratedObjectives()
        );

        response.setGeneratedKnowledge(
                module.getGeneratedKnowledge()
        );

        response.setGeneratedExamples(
                module.getGeneratedExamples()
        );

        response.setGeneratedSummary(
                module.getGeneratedSummary()
        );

        response.setStatus(
                module.getStatus()
        );

        response.setAiGenerationStatus(
                module.getAiGenerationStatus()
        );

        return response;
    }
}
```

## backend/src/main/java/com/ptc/halo/controller/StudentLessonExceptionHandler.java

```java
package com.ptc.halo.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {StudentMentorController.class, StudentAiLearningController.class,
        StudentLearningProgressionController.class, StudentAssessmentController.class})
public class StudentLessonExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(StudentLessonExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> status(ResponseStatusException error, HttpServletRequest request) {
        return response(error.getStatusCode().value(), error.getReason() == null ? "REQUEST_FAILED" : error.getReason(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException error, HttpServletRequest request) {
        return response(403, "INVALID_ROLE_OR_ACCESS", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception error, HttpServletRequest request) {
        // Do not expose credentials, upstream payloads or stack traces to the browser.
        log.error("request_failed method={} path={} exceptionType={}", request.getMethod(),
                request.getRequestURI(), error.getClass().getName());
        return response(500, "INTERNAL_SERVER_ERROR", request);
    }

    private ResponseEntity<Map<String, Object>> response(int status, String code, HttpServletRequest request) {
        log.warn("request_rejected method={} path={} status={} reason={}",
                request.getMethod(), request.getRequestURI(), status, code);
        return ResponseEntity.status(status).body(Map.of("status", status, "code", code, "message", code));
    }
}

```

## backend/src/main/java/com/ptc/halo/controller/StudentMentorController.java

```java
package com.ptc.halo.controller;

import com.ptc.halo.dtoRequest.MentorMessageRequest;
import com.ptc.halo.dtoResponse.MentorConversationResponse;
import com.ptc.halo.dtoResponse.MentorSessionResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.service.StudentLessonAccessService;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ptc.halo.service.MentorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasRole('STUDENT')")
@RequestMapping("/api/student/mentor")
public class StudentMentorController {

    private final MentorService mentorService;
    private final StudentLessonAccessService access;

    public StudentMentorController(
            MentorService mentorService,
            StudentLessonAccessService access) {

        this.mentorService = mentorService;
        this.access = access;
    }
    @PostMapping("/message/{sessionId}")
    public ResponseEntity<MentorSessionResponse> sendMessage(
            @PathVariable("sessionId") Long sessionId,
            @RequestBody MentorMessageRequest request,
            Authentication authentication) {

        UserEntity student = access.requireStudent(authentication);

        MentorSessionResponse response =
                mentorService.sendMessage(
                        sessionId,
                        student,
                        request.getMessage()
                );

        return ResponseEntity.ok(response);
    }
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<MentorConversationResponse> getConversation(
            @PathVariable("sessionId") Long sessionId,
            Authentication authentication) {

        UserEntity student = access.requireStudent(authentication);

        MentorConversationResponse response =
                mentorService.getConversation(
                        sessionId,
                        student
                );

        return ResponseEntity.ok(response);
    }
    @PostMapping("/open/{moduleId}")
    public ResponseEntity<MentorConversationResponse> openSession(
            @PathVariable("moduleId") Long moduleId,
            Authentication authentication) {

        UserEntity student = access.requireStudent(authentication);

        MentorConversationResponse response =
                mentorService.openSession(
                        moduleId,
                        student
                );

        return ResponseEntity.ok(response);
    }
}
```

## backend/src/main/java/com/ptc/halo/repository/AiLearningModuleRepository.java

```java
package com.ptc.halo.repository;

import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.enums.LessonStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

import java.util.Optional;

public interface AiLearningModuleRepository
        extends JpaRepository<AiLearningModuleEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from AiLearningModuleEntity m where m.id = :id")
    Optional<AiLearningModuleEntity> findForMentorOpenById(@org.springframework.data.repository.query.Param("id") Long id);


    Optional<AiLearningModuleEntity> findByWeekId(Long weekId);

    Optional<AiLearningModuleEntity> findByWeekIdAndStatus(Long weekId, LessonStatus status);

    List<AiLearningModuleEntity>
    findByWeek_Subject_IdAndStatus(
            Long subjectId,
            LessonStatus status
    );

    @EntityGraph(attributePaths = {"files"})
    Optional<AiLearningModuleEntity> findWithFilesById(Long id);

    long countByStatus(LessonStatus status);
}

```

## backend/src/main/java/com/ptc/halo/security/ApiSecurityErrors.java

```java
package com.ptc.halo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApiSecurityErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiSecurityErrors.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private ApiSecurityErrors() {}

    public static void write(HttpServletRequest request, HttpServletResponse response,
                             int status, String code) throws IOException {
        log.warn("security_denied method={} path={} status={} reason={}",
                request.getMethod(), request.getRequestURI(), status, code);
        response.setStatus(status);
        response.setContentType("application/json");
        mapper.writeValue(response.getOutputStream(), Map.of("status", status, "code", code, "message", code));
    }
}

```

## backend/src/main/java/com/ptc/halo/security/JwtAuthenticationFilter.java

```java
package com.ptc.halo.security;

import com.ptc.halo.service.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") &&
                SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // extractUsername verifies the signature and expiry through JwtService's parser.
                String email = jwtService.extractUsername(header.substring(7));
                if (email == null || email.isBlank()) throw new IllegalArgumentException("Missing subject");
                UserDetails details = userDetailsService.loadUserByUsername(email);
                if (!details.isEnabled() || !details.isAccountNonLocked() || !details.isAccountNonExpired()) {
                    ApiSecurityErrors.write(request, response, 403, "ACCOUNT_INACTIVE");
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
            } catch (JwtException | IllegalArgumentException | AuthenticationException invalid) {
                SecurityContextHolder.clearContext();
                ApiSecurityErrors.write(request, response, 401, "INVALID_OR_EXPIRED_TOKEN");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}

```

## backend/src/main/java/com/ptc/halo/service/MentorService.java

```java
package com.ptc.halo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.dtoResponse.MentorConversationResponse;
import com.ptc.halo.dtoResponse.MentorMessageResponse;
import com.ptc.halo.dtoResponse.MentorSessionResponse;
import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.entity.MentorMessageEntity;
import com.ptc.halo.entity.MentorSessionEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.LessonStatus;
import com.ptc.halo.enums.MentorProgressStatus;
import com.ptc.halo.enums.MessageSender;
import com.ptc.halo.repository.AiLearningModuleRepository;
import com.ptc.halo.repository.MentorMessageRepository;
import com.ptc.halo.repository.MentorSessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MentorService {
    private static final Logger log = LoggerFactory.getLogger(MentorService.class);

    private final ChatClient chatClient;
    private final AiLearningModuleRepository aiLearningModuleRepository;
    private final MentorSessionRepository mentorSessionRepository;
    private final MentorMessageRepository mentorMessageRepository;
    private final ObjectMapper objectMapper;
    private final StudentLearningProgressionService progressionService;

    public MentorService(
            ChatClient.Builder chatClientBuilder,
            AiLearningModuleRepository aiLearningModuleRepository,
            MentorSessionRepository mentorSessionRepository,
            MentorMessageRepository mentorMessageRepository, ObjectMapper objectMapper, StudentLearningProgressionService progressionService) {

        this.chatClient = chatClientBuilder.build();
        this.aiLearningModuleRepository =
                aiLearningModuleRepository;
        this.mentorSessionRepository =
                mentorSessionRepository;
        this.mentorMessageRepository =
                mentorMessageRepository;
        this.objectMapper = objectMapper;
        this.progressionService = progressionService;
    }

    @Transactional
    public MentorSessionResponse sendMessage(
            Long sessionId,
            UserEntity student,
            String studentMessage) {

        MentorSessionEntity session =
                mentorSessionRepository.findById(sessionId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND")
                        );


        if (!session.getStudent().getId()
                .equals(student.getId())) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_NOT_OWNED");
        }

        AiLearningModuleEntity module =
                session.getModule();

        progressionService.validateModuleAccess(
                module.getId(),
                student
        );

        if (module.getStatus() != LessonStatus.APPROVED) {

            throw new ResponseStatusException(HttpStatus.CONFLICT, "LESSON_NOT_APPROVED");
        }

        if (studentMessage == null || studentMessage.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MESSAGE_REQUIRED");
        }

        MentorMessageEntity studentMessageEntity =
                new MentorMessageEntity();

        studentMessageEntity.setSession(session);
        studentMessageEntity.setSender(
                MessageSender.STUDENT
        );
        studentMessageEntity.setMessage(
                studentMessage
        );
        studentMessageEntity.setCreatedAt(
                LocalDateTime.now()
        );

        mentorMessageRepository.save(
                studentMessageEntity
        );


        List<MentorMessageEntity> recentMessages =
                mentorMessageRepository
                        .findTop10BySessionIdOrderByCreatedAtDesc(
                                sessionId
                        );

        List<MentorMessageEntity> conversationContext =
                new ArrayList<>(recentMessages);

        Collections.reverse(conversationContext);

        StringBuilder conversationText =
                new StringBuilder();

        for (MentorMessageEntity message :
                conversationContext) {

            conversationText
                    .append(message.getSender())
                    .append(": ")
                    .append(message.getMessage())
                    .append("\n");
        }

        String prompt = """
            You are HALO, an AI mentor for Hospitality students.

            You are teaching a beginner student.

            APPROVED LESSON:

            Lesson:
            %s

            Learning Objectives:
            %s

            Key Knowledge:
            %s

            Examples:
            %s

            Summary:
            %s
            


            The student has just answered.

            Your job is to continue teaching the student.

                IMPORTANT RULES:
                
                HALO MENTOR RULES
                
                    You are HALO, an AI mentor for Hospitality students.
                
                    Your role is to teach and guide the student through a natural,
                    friendly conversation. You are NOT an examiner and you are NOT
                    a textbook.
                
                    GENERAL TEACHING RULES:
                
                    1. Teach one small concept at a time.
                
                    2. Keep responses short, clear, and beginner-friendly.
                
                    3. Usually respond in around 40-80 words.
                
                    4. Do not overwhelm the student with large explanations.
                
                    5. Do not dump the entire lesson in one response.
                
                    6. Do not simply repeat or copy the generated lesson.
                
                    7. Use the approved lesson as your knowledge source.
                
                    8. Do not introduce information unrelated to the approved lesson
                       unless it is necessary to answer the student's question.
                
                    9. Use simple language appropriate for beginner Hospitality students.
                
                    10. Be friendly, encouraging, patient, and respectful.
                
                    11. Talk naturally, as if you are a personal tutor.
                
                    12. Avoid overly enthusiastic or artificial phrases such as:
                        "I'm super excited!"
                        "Let's dive into the exciting world of..."
                        "behind-the-scenes magic..."
                        unless they are genuinely appropriate.
                
                    CONVERSATION RULES:
                
                    13. Do NOT ask a question in every response.
                
                    14. Do NOT make every student message feel like an assessment.
                
                    15. The student should be able to ask HALO questions at any time.
                
                    16. If the student asks a question, answer that question first
                        before continuing the lesson.
                
                    17. Do not force the student back to the previous question.
                
                    18. Allow natural conversation and follow the student's interests
                        when they are related to the lesson.
                
                    19. Do not require every student response to have a correct answer.
                
                    20. Sometimes simply explain, clarify, encourage, or give an example
                        without asking the student anything.
                
                    TEACHING METHODS:
                
                    Choose the most appropriate method for the current conversation.
                
                    Possible methods:
                
                    - EXPLAIN
                    - EXAMPLE
                    - SCENARIO
                    - DISCUSSION
                    - REFLECTION
                    - FOLLOW_UP
                    - STUDENT_QUESTION
                    - MINI_CHALLENGE
                    - CLARIFICATION
                    - ENCOURAGEMENT
                
                    Do not use the same method repeatedly.
                
                    EXPLAIN:
                
                    Use when the student needs to learn a new concept.
                
                    Explain the concept briefly and clearly.
                
                    EXAMPLE:
                
                    Use a practical Hospitality example when it helps the student
                    understand the concept.
                
                    SCENARIO:
                
                    Use realistic Hospitality situations where the student can think
                    about what they would do.
                
                    DISCUSSION:
                
                    Invite the student to share an idea or opinion.
                
                    REFLECTION:
                
                    Ask the student to connect the lesson to something they already know
                    or have experienced.
                
                    FOLLOW_UP:
                
                    Ask one simple question when asking a question would help continue
                    the learning.
                
                    STUDENT_QUESTION:
                
                    When the student asks a question, answer it directly and clearly.
                
                    MINI_CHALLENGE:
                
                    Occasionally give a small challenge to check understanding.
                
                    Do not use mini-challenges too frequently.
                
                    CLARIFICATION:
                
                    If the student seems confused, explain the concept in a simpler way
                    using a different example.
                
                    ENCOURAGEMENT:
                
                    If the student gives a good answer or makes progress, acknowledge it
                    briefly and continue naturally.
                
                    QUESTION RULES:
                
                    21. Questions should be used as a teaching tool, not as an interrogation.
                
                    22. Do not ask multiple questions in one response.
                
                    23. Ask only ONE meaningful question when a question is appropriate.
                
                    24. Do not immediately ask another question after every student answer.
                
                    25. Sometimes continue teaching without asking anything.
                
                    26. Prefer open-ended questions when discussion is appropriate.
                
                    27. Do not repeatedly ask definition-based questions.
                
                    28. Avoid questions that feel like memorization tests.
                
                    29. Prefer practical Hospitality situations over simple
                        "What is...?" questions.
                
                    30. If the student has already demonstrated understanding,
                        do not keep testing the same concept.
                
                    STUDENT ANSWER RULES:
                
                    31. If the answer is correct, briefly acknowledge it and add a small
                        useful explanation.
                
                    32. If the answer is partially correct, explain what they got right
                        and gently add what is missing.
                
                    33. If the answer is incorrect, do not shame or discourage the student.
                
                    34. When the student is wrong, explain the concept simply and provide
                        another example when useful.
                
                    35. Do not immediately give a score for normal conversation.
                
                    36. Do not label normal responses as "correct" or "incorrect"
                        unless the interaction is specifically a mini-challenge
                        or assessment.
                
                    37. Encourage students to think rather than simply memorize answers.
                
                    SCENARIO RULES:
                
                    38. Use realistic Hospitality situations whenever appropriate.
                
                    39. Scenarios should be short and easy to understand.
                
                    40. Do not turn every scenario into a graded question.
                
                    41. Sometimes discuss what would happen in the situation instead
                        of asking the student to choose a correct answer.
                
                    42. Connect scenarios to real Hospitality work such as:
                        - guest service
                        - bar operations
                        - food service
                        - cleanliness
                        - preparation
                        - communication
                        - teamwork
                        - handling guests
                        - workplace procedures
                
                    ASSESSMENT BOUNDARY:
                
                    43. Normal mentoring is NOT an assessment.
                
                    44. Do not continuously score the student's responses.
                
                    45. Do not give the student a score unless the system specifically
                        enters an assessment or mini-challenge mode.
                
                    46. Formative checks should be occasional and conversational.
                
                    47. The student should be able to learn without feeling like they
                        are constantly being tested.
                
                    48. A formal assessment should be a separate stage from normal
                        mentoring.
                
                    RESPONSE LENGTH AND FLEXIBILITY:
                
                                                                     49. Keep normal teaching responses reasonably short and easy to read.
                
                                                                     50. Around 40-80 words is a useful default for normal conversation,
                                                                         but this is NOT a strict limit.
                
                                                                     51. Adjust the response length based on what the student needs.
                
                                                                     52. If the student asks for a detailed explanation, provide a more
                                                                         detailed explanation.
                
                                                                     53. If the student asks for a short or simple explanation, keep it short.
                
                                                                     54. If the student says they do not understand, explain the concept
                                                                         more slowly and use a simpler example.
                
                                                                     55. If the student asks for more information, expand the explanation
                                                                         instead of refusing or repeating the same short answer.
                
                                                                     56. If the student's question can be answered simply, do not make the
                                                                         response unnecessarily long.
                
                                                                     57. Never dump the entire lesson into one response unless the student
                                                                         specifically asks to see the complete lesson.
                
                                                                     58. Use short paragraphs and organize longer explanations clearly.
                
                                                                     59. Avoid unnecessary repetition.
                
                                                                     60. The goal is to provide the amount of information the student needs,
                                                                         not to follow a fixed word count.
                                                                         
                 ADAPTIVE TEACHING:
                
                     61. Pay attention to what the student is asking for.
                
                     62. Adapt the explanation to the student's apparent level of understanding.
                
                     63. If the student asks for "more detail", expand the current concept.
                
                     64. If the student asks "why", explain the reasoning behind the concept,
                         not just the definition.
                
                     65. If the student asks "how", explain the process step by step.
                
                     66. If the student asks for an example, provide a practical Hospitality
                         example.
                
                     67. If the student asks for a real-world situation, provide a realistic
                         Hospitality scenario.
                
                     68. If the student says they are confused, simplify the explanation and
                         use a different example.
                
                     69. If the student demonstrates strong understanding, the explanation
                         may gradually become more detailed or advanced.
                
                     70. Do not treat every student response as an answer that must be evaluated.
                
                     71. The conversation should naturally switch between explanation,
                         examples, scenarios, discussion, clarification, and questions.
                
                     72. Do not force a question at the end of every response.
                
                     73. The student's request for information takes priority over continuing
                         a previously planned teaching question.
                
                     74. If the student asks something directly related to the lesson,
                         answer it before continuing the lesson.
                
                     75. If the student asks something unrelated, briefly explain that HALO
                         is focused on the current lesson and redirect them when appropriate.
                        
                        Respond ONLY with the natural message HALO should send to the student.
                
                            Do not return JSON.
                            Do not include labels such as "message", "concept", or "conceptCovered".
                            Do not include internal reasoning or teaching instructions.
             
                
                    MOST IMPORTANT RULE:
                
                    HALO should feel like a patient Hospitality mentor having a conversation
                    with a student.
                
                    The student should feel that they are LEARNING WITH HALO,
                    not being QUESTIONED BY HALO.
   
            Student's latest answer:

            %s

            Respond as HALO.
            """.formatted(
                module.getLessonText(),
                module.getGeneratedObjectives(),
                module.getGeneratedKnowledge(),
                module.getGeneratedExamples(),
                module.getGeneratedSummary(),
                conversationText,
                studentMessage
        );

        String haloMessage;
        try {
            haloMessage = chatClient.prompt().user(prompt).call().content();
        } catch (Exception failure) {
            log.error("mentor_provider_failure sessionId={} moduleId={} exceptionType={}",
                    sessionId, module.getId(), failure.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MENTOR_PROVIDER_UNAVAILABLE");
        }

        if (haloMessage == null ||
                haloMessage.isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "MENTOR_EMPTY_RESPONSE");
        }


        MentorMessageEntity haloMessageEntity =
                new MentorMessageEntity();

        haloMessageEntity.setSession(session);

        haloMessageEntity.setSender(
                MessageSender.HALO
        );

        haloMessageEntity.setMessage(
                haloMessage
        );

        haloMessageEntity.setCreatedAt(
                LocalDateTime.now()
        );

        mentorMessageRepository.save(
                haloMessageEntity
        );


        session.setLastActivityAt(
                LocalDateTime.now()
        );

        session.setProgressStatus(
                MentorProgressStatus.LEARNING
        );

        mentorSessionRepository.save(session);


        MentorSessionResponse response =
                new MentorSessionResponse();

        response.setSessionId(
                session.getId()
        );

        response.setModuleId(
                module.getId()
        );

        response.setHaloMessage(
                haloMessage
        );

        return response;
    }
    @Transactional(readOnly = true)
    public MentorConversationResponse getConversation(
            Long sessionId,
            UserEntity student) {

        MentorSessionEntity session =
                mentorSessionRepository.findById(sessionId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND")
                        );

        if (!session.getStudent().getId()
                .equals(student.getId())) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SESSION_NOT_OWNED");
        }

        progressionService.validateModuleAccess(session.getModule().getId(), student);

        List<MentorMessageEntity> messages =
                mentorMessageRepository
                        .findBySessionIdOrderByCreatedAtAsc(
                                sessionId
                        );

        List<MentorMessageResponse> messageResponses =
                messages.stream()
                        .map(message -> {

                            MentorMessageResponse response =
                                    new MentorMessageResponse();

                            response.setId(
                                    message.getId()
                            );

                            response.setSender(
                                    message.getSender()
                            );

                            response.setMessage(
                                    message.getMessage()
                            );

                            response.setCreatedAt(
                                    message.getCreatedAt()
                            );

                            return response;

                        })
                        .toList();

        MentorConversationResponse response =
                new MentorConversationResponse();

        response.setSessionId(
                session.getId()
        );

        response.setModuleId(
                session.getModule().getId()
        );

        response.setMessages(
                messageResponses
        );

        return response;
    }
    @Transactional
    public MentorConversationResponse openSession(
            Long moduleId,
            UserEntity student) {

        progressionService.validateModuleAccess(
                moduleId,
                student
        );

        AiLearningModuleEntity module =
                aiLearningModuleRepository.findForMentorOpenById(moduleId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND")
                        );

        if (module.getStatus() != LessonStatus.APPROVED) {

            throw new ResponseStatusException(HttpStatus.CONFLICT, "LESSON_NOT_APPROVED");
        }


        MentorSessionEntity session =
                mentorSessionRepository
                        .findByStudentIdAndModuleId(
                                student.getId(),
                                moduleId
                        )
                        .orElse(null);


        if (session == null) {

            session = new MentorSessionEntity();

            session.setStudent(student);
            session.setModule(module);
            session.setStartedAt(
                    LocalDateTime.now()
            );
            session.setLastActivityAt(
                    LocalDateTime.now()
            );

            session.setProgressStatus(
                    MentorProgressStatus.LEARNING
            );

            session =
                    mentorSessionRepository.save(session);


            String haloMessage = "Welcome! Review the published lesson below, then ask me about any concept or example.";

            MentorMessageEntity message =
                    new MentorMessageEntity();

            message.setSession(session);
            message.setSender(MessageSender.HALO);
            message.setMessage(haloMessage);
            message.setCreatedAt(
                    LocalDateTime.now()
            );

            mentorMessageRepository.save(message);

            session.setLastActivityAt(
                    LocalDateTime.now()
            );

            mentorSessionRepository.save(session);
        }


        List<MentorMessageEntity> messages =
                mentorMessageRepository
                        .findBySessionIdOrderByCreatedAtAsc(
                                session.getId()
                        );

        List<MentorMessageResponse> messageResponses =
                messages.stream()
                        .map(message -> {

                            MentorMessageResponse response =
                                    new MentorMessageResponse();

                            response.setId(
                                    message.getId()
                            );

                            response.setSender(
                                    message.getSender()
                            );

                            response.setMessage(
                                    message.getMessage()
                            );

                            response.setCreatedAt(
                                    message.getCreatedAt()
                            );

                            return response;

                        })
                        .toList();

        MentorConversationResponse response =
                new MentorConversationResponse();

        response.setSessionId(
                session.getId()
        );

        response.setModuleId(
                moduleId
        );

        response.setMessages(
                messageResponses
        );

        return response;
    }

}
```

## backend/src/main/java/com/ptc/halo/service/StudentLearningProgressionService.java

```java
package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.StudentWeekAccessResponse;
import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.entity.StudentModuleProgressEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.entity.WeekEntity;
import com.ptc.halo.enums.LessonStatus;
import com.ptc.halo.repository.AiLearningModuleRepository;
import com.ptc.halo.repository.StudentModuleProgressRepository;
import com.ptc.halo.repository.SubjectRepository;
import com.ptc.halo.repository.WeekRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.ptc.halo.enums.AiGenerationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StudentLearningProgressionService {
    private final StudentLessonAccessService access;

    private final WeekRepository weekRepository;

    private final SubjectRepository subjectRepository;

    private final AiLearningModuleRepository
            aiLearningModuleRepository;

    private final StudentModuleProgressRepository
            studentModuleProgressRepository;


    public StudentLearningProgressionService(
            WeekRepository weekRepository,
            SubjectRepository subjectRepository,
            AiLearningModuleRepository aiLearningModuleRepository,
            StudentModuleProgressRepository studentModuleProgressRepository, StudentLessonAccessService access) {
        this.access = access;

        this.weekRepository = weekRepository;

        this.subjectRepository = subjectRepository;

        this.aiLearningModuleRepository =
                aiLearningModuleRepository;

        this.studentModuleProgressRepository =
                studentModuleProgressRepository;
    }


    public List<StudentWeekAccessResponse> getWeekAccess(
            Long subjectId,
            UserEntity student) {

        // Enforce the same subject eligibility used by the student subject list.
        var subject = subjectRepository.findById(subjectId)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "SUBJECT_NOT_FOUND")
                );


        access.validateSubject(student, subject);

        List<WeekEntity> weeks =
                weekRepository
                        .findBySubject_IdOrderByWeekNumberAsc(
                                subjectId
                        );


        List<StudentWeekAccessResponse> responses =
                new ArrayList<>();


        for (int i = 0; i < weeks.size(); i++) {

            WeekEntity week =
                    weeks.get(i);


            Optional<AiLearningModuleEntity> moduleOptional =
                    aiLearningModuleRepository
                            .findByWeekIdAndStatus(
                                    week.getId(),
                                    LessonStatus.APPROVED
                            ).filter(module -> module.getAiGenerationStatus() == AiGenerationStatus.COMPLETED);


            boolean lessonAvailable =
                    moduleOptional.isPresent();


            boolean completed = false;


            if (moduleOptional.isPresent()) {

                AiLearningModuleEntity module =
                        moduleOptional.get();


                Optional<StudentModuleProgressEntity> progressOptional =
                        studentModuleProgressRepository
                                .findByStudentIdAndModuleId(
                                        student.getId(),
                                        module.getId()
                                );


                completed =
                        progressOptional.isPresent()
                                &&
                                Boolean.TRUE.equals(
                                        progressOptional
                                                .get()
                                                .getCompleted()
                                );
            }


            boolean unlocked;


            // First week is always unlocked
            if (i == 0) {

                unlocked = true;

            } else {

                WeekEntity previousWeek =
                        weeks.get(i - 1);


                unlocked =
                        isPreviousWeekCompleted(
                                previousWeek,
                                student
                        );
            }


            StudentWeekAccessResponse response =
                    new StudentWeekAccessResponse();


            response.setWeekId(
                    week.getId()
            );

            response.setWeekNumber(
                    week.getWeekNumber()
            );

            response.setTitle(
                    week.getTitle()
            );

            response.setUnlocked(
                    unlocked
            );

            response.setCompleted(
                    completed
            );

            response.setLessonAvailable(
                    lessonAvailable
            );


            if (moduleOptional.isPresent()) {

                response.setModuleId(
                        moduleOptional
                                .get()
                                .getId()
                );

            } else {

                response.setModuleId(null);
            }


            responses.add(response);
        }


        return responses;
    }


    private boolean isPreviousWeekCompleted(
            WeekEntity previousWeek,
            UserEntity student) {


        Optional<AiLearningModuleEntity> previousModuleOptional =
                aiLearningModuleRepository
                        .findByWeekIdAndStatus(
                                previousWeek.getId(),
                                LessonStatus.APPROVED
                        ).filter(module -> module.getAiGenerationStatus() == AiGenerationStatus.COMPLETED);


        if (previousModuleOptional.isEmpty()) {
            return false;
        }


        AiLearningModuleEntity previousModule =
                previousModuleOptional.get();


        Optional<StudentModuleProgressEntity> progressOptional =
                studentModuleProgressRepository
                        .findByStudentIdAndModuleId(
                                student.getId(),
                                previousModule.getId()
                        );


        if (progressOptional.isEmpty()) {
            return false;
        }


        return Boolean.TRUE.equals(
                progressOptional
                        .get()
                        .getCompleted()
        );
    }
    public void validateModuleAccess(
            Long moduleId,
            UserEntity student) {

        AiLearningModuleEntity module =
                aiLearningModuleRepository
                        .findById(moduleId)
                        .orElseThrow(() ->
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "MODULE_NOT_FOUND")
                        );

        WeekEntity currentWeek =
                module.getWeek();

        access.validatePublished(module, student);

        Long subjectId =
                currentWeek
                        .getSubject()
                        .getId();

        List<WeekEntity> weeks =
                weekRepository
                        .findBySubject_IdOrderByWeekNumberAsc(
                                subjectId
                        );

        int currentIndex = -1;

        for (int i = 0; i < weeks.size(); i++) {

            if (weeks.get(i)
                    .getId()
                    .equals(currentWeek.getId())) {

                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {

            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MODULE_WEEK_NOT_FOUND");
        }

        // First week is always unlocked
        if (currentIndex == 0) {
            return;
        }

        WeekEntity previousWeek =
                weeks.get(currentIndex - 1);

        boolean previousCompleted =
                isPreviousWeekCompleted(
                        previousWeek,
                        student
                );

        if (!previousCompleted) {

            throw new ResponseStatusException(HttpStatus.CONFLICT, "PREVIOUS_WEEK_INCOMPLETE");
        }
    }
}
```

## backend/src/main/java/com/ptc/halo/service/StudentLessonAccessService.java

```java
package com.ptc.halo.service;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentLessonAccessService {
    private final UserRepository users;
    private final StudentProfileRepository profiles;

    public StudentLessonAccessService(UserRepository users, StudentProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    public UserEntity requireStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        }
        UserEntity student = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED"));
        validateStudent(student);
        return student;
    }

    public void validateStudent(UserEntity student) {
        if (student == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        if (student.getRole() != Role.STUDENT) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INVALID_ROLE");
        if (student.getStatus() != Status.ACTIVE) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACCOUNT_INACTIVE");
    }

    public void validateSubject(UserEntity student, SubjectEntity subject) {
        validateStudent(student);
        StudentProfileEntity profile = profiles.findByUserId(student.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ENROLLED"));
        // Existing enrollment model: subjects are assigned by student year level.
        if (profile.getYearLevel() == null || profile.getYearLevel() != subject.getYearLevel()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ENROLLED");
        }
    }

    public void validatePublished(AiLearningModuleEntity module, UserEntity student) {
        validateSubject(student, module.getWeek().getSubject());
        if (module.getStatus() != LessonStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LESSON_NOT_APPROVED");
        }
        if (module.getAiGenerationStatus() != AiGenerationStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LESSON_NOT_GENERATED");
        }
    }
}

```

## backend/src/test/java/com/ptc/halo/MentorOpenTest.java

```java
package com.ptc.halo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MentorOpenTest {
    @Test void newAndExistingSessionsOpenWithoutCallingGemini() {
        var builder = mock(ChatClient.Builder.class); var client = mock(ChatClient.class);
        when(builder.build()).thenReturn(client);
        var modules = mock(AiLearningModuleRepository.class);
        var sessions = mock(MentorSessionRepository.class);
        var messages = mock(MentorMessageRepository.class);
        var progression = mock(StudentLearningProgressionService.class);
        var service = new MentorService(builder, modules, sessions, messages, new ObjectMapper(), progression);
        var student = new UserEntity(); student.setId(8L);
        var module = mock(AiLearningModuleEntity.class);
        when(module.getStatus()).thenReturn(LessonStatus.APPROVED);
        when(modules.findForMentorOpenById(15L)).thenReturn(Optional.of(module));
        when(sessions.findByStudentIdAndModuleId(8L, 15L)).thenReturn(Optional.empty());
        var saved = mock(MentorSessionEntity.class); when(saved.getId()).thenReturn(21L);
        when(sessions.save(any())).thenReturn(saved);
        when(messages.findBySessionIdOrderByCreatedAtAsc(21L)).thenReturn(List.of());
        var result = service.openSession(15L, student);
        assertEquals(15L, result.getModuleId()); assertEquals(21L, result.getSessionId());
        verify(progression).validateModuleAccess(15L, student);
        verify(messages).save(any());
        verifyNoInteractions(client);
        when(sessions.findByStudentIdAndModuleId(8L, 15L)).thenReturn(Optional.of(saved));
        service.openSession(15L, student);
        verify(messages, times(1)).save(any()); verifyNoInteractions(client);
    }
}

```

## backend/src/test/java/com/ptc/halo/StudentLessonAccessTest.java

```java
package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentLessonAccessTest {
    UserRepository users = mock(UserRepository.class);
    StudentProfileRepository profiles = mock(StudentProfileRepository.class);
    StudentLessonAccessService access = new StudentLessonAccessService(users, profiles);
    UserEntity student = new UserEntity();
    SubjectEntity subject = new SubjectEntity();
    AiLearningModuleEntity module = mock(AiLearningModuleEntity.class);
    WeekEntity week = mock(WeekEntity.class);

    @BeforeEach void setup() {
        student.setId(8L); student.setRole(Role.STUDENT); student.setStatus(Status.ACTIVE);
        subject.setYearLevel(YearLevel.FIRST_YEAR);
        StudentProfileEntity profile = new StudentProfileEntity();
        profile.setYearLevel(YearLevel.FIRST_YEAR);
        when(profiles.findByUserId(8L)).thenReturn(Optional.of(profile));
        when(module.getWeek()).thenReturn(week); when(week.getSubject()).thenReturn(subject);
        when(module.getStatus()).thenReturn(LessonStatus.APPROVED);
        when(module.getAiGenerationStatus()).thenReturn(AiGenerationStatus.COMPLETED);
    }
    void denied(int status, String reason, Runnable action) {
        var error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(status, error.getStatusCode().value()); assertEquals(reason, error.getReason());
    }
    @Test void approvedAndCompletedIsAccessible() { assertDoesNotThrow(() -> access.validatePublished(module, student)); }
    @Test void wrongYearIsForbidden() { subject.setYearLevel(YearLevel.SECOND_YEAR); denied(403, "STUDENT_NOT_ENROLLED", () -> access.validatePublished(module, student)); }
    @Test void missingProfileIsForbidden() { when(profiles.findByUserId(8L)).thenReturn(Optional.empty()); denied(403, "STUDENT_NOT_ENROLLED", () -> access.validatePublished(module, student)); }
    @Test void wrongRoleIsForbidden() { student.setRole(Role.PROFESSOR); denied(403, "INVALID_ROLE", () -> access.validatePublished(module, student)); }
    @Test void inactiveIsForbidden() { student.setStatus(Status.BLOCKED); denied(403, "ACCOUNT_INACTIVE", () -> access.validatePublished(module, student)); }
    @Test void unpublishedIsConflict() { when(module.getStatus()).thenReturn(LessonStatus.PENDING); denied(409, "LESSON_NOT_APPROVED", () -> access.validatePublished(module, student)); }
    @Test void incompleteGenerationIsConflict() { when(module.getAiGenerationStatus()).thenReturn(AiGenerationStatus.PENDING); denied(409, "LESSON_NOT_GENERATED", () -> access.validatePublished(module, student)); }
    @Test void missingAuthenticationIsUnauthorized() { denied(401, "AUTHENTICATION_REQUIRED", () -> access.requireStudent(null)); }
    @Test void firstWeekStillChecksSubjectAndPublication() {
        var weeks = mock(WeekRepository.class); var modules = mock(AiLearningModuleRepository.class);
        var progress = mock(StudentModuleProgressRepository.class);
        var service = new StudentLearningProgressionService(weeks, mock(SubjectRepository.class), modules, progress, access);
        when(modules.findById(15L)).thenReturn(Optional.of(module));
        when(week.getId()).thenReturn(4L);
        when(weeks.findBySubject_IdOrderByWeekNumberAsc(null)).thenReturn(List.of(week));
        assertDoesNotThrow(() -> service.validateModuleAccess(15L, student));
        subject.setYearLevel(YearLevel.SECOND_YEAR);
        denied(403, "STUDENT_NOT_ENROLLED", () -> service.validateModuleAccess(15L, student));
        denied(404, "MODULE_NOT_FOUND", () -> service.validateModuleAccess(999L, student));
    }
}

```

## backend/src/test/java/com/ptc/halo/StudentMentorHttpTest.java

```java
package com.ptc.halo;

import com.ptc.halo.config.SecurityConfig;
import com.ptc.halo.controller.StudentMentorController;
import com.ptc.halo.dtoResponse.MentorConversationResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.security.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentMentorController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StudentMentorHttpTest {
    @Autowired MockMvc mvc;
    @MockBean MentorService mentor;
    @MockBean StudentLessonAccessService access;
    @MockBean CustomUserDetailsService details;
    @MockBean JwtService jwt;

    void token(String role) {
        when(jwt.extractUsername("test-token")).thenReturn("student@example.test");
        when(details.loadUserByUsername("student@example.test")).thenReturn(
                User.withUsername("student@example.test").password("unused").roles(role).build());
    }
    @Test void bearerStudentPostReturns200WithModuleId() throws Exception {
        token("STUDENT"); var student = new UserEntity();
        when(access.requireStudent(any())).thenReturn(student);
        var response = new MentorConversationResponse(); response.setSessionId(21L); response.setModuleId(15L);
        when(mentor.openSession(15L, student)).thenReturn(response);
        mvc.perform(post("/api/student/mentor/open/15").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.moduleId").value(15));
    }
    @Test void missingAuthenticationReturns401() throws Exception {
        mvc.perform(post("/api/student/mentor/open/15")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
    @Test void invalidTokenReturns401() throws Exception {
        when(jwt.extractUsername("bad")).thenThrow(new io.jsonwebtoken.MalformedJwtException("bad"));
        mvc.perform(post("/api/student/mentor/open/15").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_OR_EXPIRED_TOKEN"));
    }
    @Test void professorCannotOpenStudentSession() throws Exception {
        token("PROFESSOR"); mvc.perform(post("/api/student/mentor/open/15").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden()); verifyNoInteractions(mentor);
    }
    @Test void notEnrolledReturnsExplicit403() throws Exception {
        token("STUDENT"); when(mentor.openSession(eq(15L), any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ENROLLED"));
        mvc.perform(post("/api/student/mentor/open/15").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"));
    }
    @Test void getIs405NotMisleading403() throws Exception {
        token("STUDENT"); mvc.perform(get("/api/student/mentor/open/15").header("Authorization", "Bearer test-token"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }
    @Test void unexpectedFailureIs500Not403() throws Exception {
        token("STUDENT"); when(mentor.openSession(eq(15L), any())).thenThrow(new RuntimeException("internal detail"));
        mvc.perform(post("/api/student/mentor/open/15").header("Authorization", "Bearer test-token"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }
}

```

## frontend/src/pages/student/components/LessonChat.jsx

```javascript
import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Bot, Send, Volume2, VolumeX } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getWeekLesson } from '../../../services/student/studentService';
import { openSession, sendMessage as sendMentorMessage } from '../../../services/student/aiMentorService';
import { useTextToSpeech } from '../../../hooks/useTextToSpeech';
import { useToast } from '../../../context/notifications/useToast';
import { lessonErrorMessage } from '../../../utils/lessonErrors';
import styles from '../styles/LessonChat.module.css';

// Route weekId resolves to the approved module ID before opening its mentor session.
export default function LessonChat() {
  const { topicId, weekId } = useParams();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [lesson, setLesson] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [isAiTyping, setIsAiTyping] = useState(false);
  const messagesEndRef = useRef(null);
  const isMountedRef = useRef(true);
  const sendLock = useRef(false);
  const { speak, stop, speakingId, isSupported: ttsSupported } = useTextToSpeech();
  const { showToast } = useToast();

  useEffect(() => {
    isMountedRef.current = true;
    return () => { isMountedRef.current = false; };
  }, []);

  useEffect(() => {
    return () => stop();
  }, [topicId, weekId, stop]);

  useEffect(() => {
    let isMounted = true;
    const controller = new AbortController();

    async function loadLesson() {
      try {
        const module = await getWeekLesson(weekId, { signal: controller.signal });
        if (!isMounted) return;

        if (String(module.weekId) !== String(weekId)) throw new Error('Lesson/week mismatch');
        setLesson(module);
        const conversation = await openSession(module.id, { signal: controller.signal });
        if (String(conversation.moduleId) !== String(module.id) || !conversation.sessionId) {
          throw new Error('Mentor/module mismatch');
        }
        if (!isMounted) return;

        setSessionId(conversation.sessionId);
        setMessages(
          (conversation.messages ?? []).map((m) => ({
            id: m.id,
            sender: m.sender === 'STUDENT' ? 'user' : 'ai', // adjust if MessageSender enum values differ
            text: m.message,
          }))
        );
      } catch (err) {
        if (isMounted) setLoadError(lessonErrorMessage(err));
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadLesson();
    return () => { isMounted = false; controller.abort(); };
  }, [weekId, reloadKey]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isAiTyping]);

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading lesson...</p>
        </div>
      </div>
    );
  }

  function retryLesson() {
    setIsLoading(true);
    setLoadError('');
    setLesson(null);
    setSessionId(null);
    setMessages([]);
    setReloadKey((key) => key + 1);
  }

  if (loadError && !lesson) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <main className={styles.notFound}>
            <p role="alert">{loadError}</p>
            <Button onClick={retryLesson}>Retry</Button>
            <Button onClick={() => navigate('/student/subjects')}>Back to Subjects</Button>
          </main>
        </div>
      </div>
    );
  }

  async function handleSend(e) {
    e.preventDefault();
    if (sendLock.current) return;

    const text = draft.trim();
    if (!text || !sessionId) return;

    sendLock.current = true;
    const userMessage = { id: crypto.randomUUID(), sender: 'user', text };
    setMessages((prev) => [...prev, userMessage]);
    setDraft('');
    setIsAiTyping(true);

    try {
      const result = await sendMentorMessage(sessionId, text);
      if (!isMountedRef.current) return;
      setMessages((prev) => [...prev, { id: crypto.randomUUID(), sender: 'ai', text: result.haloMessage }]);
    } catch (err) {
      if (isMountedRef.current) {
        setMessages((prev) => prev.filter((message) => message.id !== userMessage.id));
        setDraft(text);
        showToast(lessonErrorMessage(err), 'error');
      }
    } finally {
      sendLock.current = false;
      if (isMountedRef.current) setIsAiTyping(false);
    }
  }

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} />

      <div className={styles.contentArea}>
        <header className={styles.topHeader}>
          <div className={styles.mentorIdentity}>
            <span className={styles.mentorAvatar}>
              <Bot size={18} />
            </span>
            <div>
              <p className={styles.mentorName}>AI Mentor</p>
              <p className={styles.mentorStatus}>Ready to help</p>
            </div>
          </div>

          <div className={styles.breadcrumb}>
            <span className={styles.breadcrumbActive}>Week {weekId}</span>
          </div>
        </header>

        <div className={styles.tabsRow}>
          <p className={styles.tabsSubtitle}>Chat with your AI Mentor</p>
          <div className={styles.tabsRowActions}>
            <Button onClick={() => navigate(`/student/quiz/${topicId}/${weekId}`)}>Start quiz</Button>
          </div>
        </div>

        <main className={styles.chatArea}>
          {lesson && (
            <section className={styles.bubble} aria-label="Published lesson">
              <h2>Published Lesson</h2>
              {[
                ['Objectives', lesson.generatedObjectives],
                ['Knowledge', lesson.generatedKnowledge],
                ['Examples', lesson.generatedExamples],
                ['Summary', lesson.generatedSummary],
              ].map(([title, content]) => (
                <section key={title}>
                  <h3>{title}</h3>
                  <p style={{ whiteSpace: 'pre-wrap' }}>{content}</p>
                </section>
              ))}
            </section>
          )}
          {loadError && (
            <div role="alert">
              <p>{loadError}</p>
              <Button onClick={retryLesson}>Retry mentor connection</Button>
            </div>
          )}
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`${styles.messageBlock} ${msg.sender === 'user' ? styles.messageBlockUser : ''}`}
            >
              <p className={styles.messageSender}>{msg.sender === 'ai' ? 'AI Mentor' : 'You'}</p>
              <div className={`${styles.bubble} ${msg.sender === 'user' ? styles.bubbleUser : styles.bubbleAi}`}>
                {msg.text}
              </div>

              {msg.sender === 'ai' && ttsSupported && (
                <button
                  type="button"
                  className={`${styles.speakBtn} ${speakingId === msg.id ? styles.speakBtnActive : ''}`}
                  onClick={() => speak(msg.id, msg.text)}
                  aria-label={speakingId === msg.id ? 'Stop reading message aloud' : 'Read message aloud'}
                >
                  {speakingId === msg.id ? <VolumeX size={14} /> : <Volume2 size={14} />}
                  {speakingId === msg.id ? 'Stop' : 'Listen'}
                </button>
              )}
            </div>
          ))}

          {isAiTyping && (
            <div className={styles.messageBlock}>
              <p className={styles.messageSender}>AI Mentor</p>
              <div className={`${styles.bubble} ${styles.bubbleAi} ${styles.bubbleTyping}`}>
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </main>

        <form className={styles.inputRow} onSubmit={handleSend}>
          <input
            type="text"
            className={styles.chatInput}
            placeholder={isAiTyping ? 'Waiting for AI Mentor to respond...' : 'Ask AI Mentor'}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            disabled={isAiTyping || !sessionId}
          />
          <button type="submit" className={styles.sendBtn} aria-label="Send message" disabled={isAiTyping || !sessionId}>
            <Send size={18} />
          </button>
        </form>
      </div>
    </div>
  );
}
```

## frontend/src/services/student/aiMentorService.js

```javascript
// API routes verified against the controllers in HALO.zip.
import apiClient from '../apiClient';

export async function openSession(moduleId, config = {}) {
  if (!/^\d+$/.test(String(moduleId)) || Number(moduleId) <= 0) {
    throw new Error('The lesson response did not include a valid module ID.');
  }
  // POST creates/resumes a session. GET is not part of this controller contract.
  const res = await apiClient.post(`/student/mentor/open/${moduleId}`, null, config);
  return res.data;
}

export async function sendMessage(sessionId, message) {
  const res = await apiClient.post(`/student/mentor/message/${sessionId}`, { message });
  return res.data;
}

export async function getConversation(sessionId) {
  const res = await apiClient.get(`/student/mentor/session/${sessionId}`);
  return res.data;
}
```

## frontend/src/utils/lessonErrors.js

```javascript
const reasons = {
  AUTHENTICATION_REQUIRED: 'Please sign in again to open this lesson.',
  INVALID_OR_EXPIRED_TOKEN: 'Your session expired. Please sign in again.',
  INVALID_ROLE: 'Sign in with a student account to open this lesson.',
  INVALID_ROLE_OR_ACCESS: 'Your account does not have access to this lesson.',
  ACCOUNT_INACTIVE: 'Your account is inactive. Contact your administrator.',
  STUDENT_NOT_ENROLLED: 'This subject is not assigned to your student year level.',
  LESSON_NOT_APPROVED: 'This lesson has not been published by your professor.',
  LESSON_NOT_GENERATED: 'This lesson is not ready. Ask your professor to regenerate and publish it.',
  PREVIOUS_WEEK_INCOMPLETE: 'Complete the previous week before opening this lesson.',
  MODULE_NOT_FOUND: 'The learning module was not found. Reopen the lesson from Subjects.',
  MODULE_WEEK_NOT_FOUND: 'This lesson no longer belongs to the selected subject.',
  SESSION_NOT_OWNED: 'This conversation belongs to another student.',
  SESSION_NOT_FOUND: 'This conversation no longer exists. Reopen the lesson.',
  METHOD_NOT_ALLOWED: 'The request method does not match the server. Refresh the app and check that the updated backend is running.',
  MENTOR_PROVIDER_UNAVAILABLE: 'The AI mentor is temporarily unavailable. Your published lesson is still available to read.',
  MENTOR_EMPTY_RESPONSE: 'The AI mentor returned no reply. Please try again.',
};

export function lessonErrorMessage(error) {
  const code = error.response?.data?.code;
  if (reasons[code]) return reasons[code];
  const status = error.response?.status;
  if (status === 401) return reasons.AUTHENTICATION_REQUIRED;
  if (status === 403) return reasons.INVALID_ROLE_OR_ACCESS;
  if (status === 404) return reasons.MODULE_NOT_FOUND;
  if (status === 502 || status === 503) return reasons.MENTOR_PROVIDER_UNAVAILABLE;
  return 'Could not load the lesson or mentor conversation. Please retry.';
}

```

## frontend/tests/api.test.mjs

```javascript
import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';

let server;
let api;
let auth;
let admin;
let professor;
let quiz;
let mentor;
let captured;
const responseData = { marker: 'response preserved' };
function memoryStorage() {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) };
}

before(async () => {
  globalThis.localStorage = memoryStorage();
  globalThis.sessionStorage = memoryStorage();
  globalThis.window = new EventTarget();
  server = await createServer({ server: { middlewareMode: true }, appType: 'custom' });
  api = (await server.ssrLoadModule('/src/services/apiClient.js')).default;
  auth = await server.ssrLoadModule('/src/services/authService.js');
  admin = await server.ssrLoadModule('/src/services/admin/adminService.js');
  professor = await server.ssrLoadModule('/src/services/professor/professorService.js');
  quiz = await server.ssrLoadModule('/src/services/student/quizService.js');
  mentor = await server.ssrLoadModule('/src/services/student/aiMentorService.js');
  api.defaults.adapter = async (config) => {
    captured = config;
    return { data: responseData, status: 200, statusText: 'OK', headers: {}, config };
  };
});

after(async () => {
  await server?.close();
  delete globalThis.localStorage;
  delete globalThis.sessionStorage;
  delete globalThis.window;
});

test('auth sends exact backend request fields and keeps public requests free of bearer tokens', async () => {
  localStorage.setItem('halo_user', JSON.stringify({ name: 'Test', email: 'test@example.test', role: 'student' }));
  localStorage.setItem('halo_token', 'fake-test-token');
  assert.deepEqual(await auth.login('test@example.test', 'test-password'), responseData);
  assert.deepEqual(JSON.parse(captured.data), { email: 'test@example.test', password: 'test-password' });
  assert.equal(captured.headers.Authorization, undefined);
  await auth.register('Test', 'test@example.test', 'test-password', 'ST-1', 'A', 'FIRST_YEAR');
  assert.deepEqual(JSON.parse(captured.data), { name: 'Test', email: 'test@example.test', password: 'test-password', studentId: 'ST-1', section: 'A', yearLevel: 'FIRST_YEAR' });
  await auth.resetPassword('test@example.test', '123456', 'new-password');
  assert.deepEqual(JSON.parse(captured.data), { email: 'test@example.test', otp: '123456', newPassword: 'new-password' });
  await auth.changePassword('old-password', 'new-password');
  assert.equal(captured.headers.Authorization, 'Bearer fake-test-token');
});

test('monitoring uses the type query parameter and forwards cancellation', async () => {
  const signal = new AbortController().signal;
  await admin.getActivityLog({ role: 'PROFESSOR', activityType: 'MODULE', signal });
  assert.equal(captured.url, '/admin/activity-logs');
  assert.deepEqual(captured.params, { role: 'PROFESSOR', type: 'MODULE' });
  assert.equal(captured.signal, signal);
});

test('professor CRUD and module upload retain numeric week numbers and multipart field names', async () => {
  await professor.addWeek(7, { weekNumber: 2, title: 'Lesson two' });
  assert.equal(captured.url, '/professor/subjects/7/weeks');
  assert.deepEqual(JSON.parse(captured.data), { weekNumber: 2, title: 'Lesson two' });
  const file = new File(['test'], 'lesson.pdf', { type: 'application/pdf' });
  await professor.createLearningModule(7, { lessonText: 'Text', youtubeLink: 'https://youtube.com/watch?v=test', aiNotes: 'Notes', files: [file] });
  assert.ok(captured.data instanceof FormData);
  assert.deepEqual([...captured.data.keys()], ['weekId', 'lessonText', 'youtubeLink', 'aiNotes', 'files']);
  assert.equal(captured.data.get('weekId'), '7');
  await professor.approveLesson(9);
  assert.equal(captured.method, 'put');
  assert.equal(captured.url, '/professor/ai-learning-modules/9/approve');
});

test('quiz and mentor requests use the controller IDs and payload wrappers', async () => {
  const answers = [{ questionId: 6, answer: 'B' }];
  assert.deepEqual(await quiz.submitAttempt(22, answers), responseData);
  assert.equal(captured.url, '/student/assessment/submit/22');
  assert.deepEqual(JSON.parse(captured.data), { answers });
  await quiz.getAttemptResult(22);
  assert.equal(captured.method, 'get');
  assert.equal(captured.url, '/student/assessment/result/22');
  await mentor.openSession(9);
  assert.equal(captured.url, '/student/mentor/open/9');
  await mentor.sendMessage(12, 'Explain this');
  assert.equal(captured.url, '/student/mentor/message/12');
  assert.deepEqual(JSON.parse(captured.data), { message: 'Explain this' });
});

test('professor draft lookup preserves saved data, treats only 204 as empty, and forwards cancellation', async () => {
  const originalAdapter = api.defaults.adapter;
  const signal = new AbortController().signal;
  const draft = { id: 9, weekId: 7, status: 'PENDING', files: [{ id: 3, originalFileName: 'lesson.pdf' }] };
  try {
    api.defaults.adapter = async (config) => {
      captured = config;
      return { data: draft, status: 200, headers: {}, config };
    };
    assert.deepEqual(await professor.getLearningModuleByWeek(7, { signal }), draft);
    assert.equal(captured.url, '/professor/ai-learning-modules/week/7');
    assert.equal(captured.signal, signal);
    api.defaults.adapter = async (config) => ({ data: '', status: 204, headers: {}, config });
    assert.equal(await professor.getLearningModuleByWeek(7), null);
    api.defaults.adapter = async () => { throw Object.assign(new Error('Week not found'), { response: { status: 404 } }); };
    await assert.rejects(professor.getLearningModuleByWeek(7), /Week not found/);
  } finally {
    api.defaults.adapter = originalAdapter;
  }
});

test('existing draft updates use JSON and individual file uploads use singular file field', async () => {
  const materials = { lessonText: 'Updated', youtubeLink: '', aiNotes: '' };
  assert.deepEqual(await professor.updateLearningModule(9, materials), responseData);
  assert.equal(captured.method, 'put');
  assert.equal(captured.url, '/professor/ai-learning-modules/9');
  assert.deepEqual(JSON.parse(captured.data), materials);
  await professor.uploadModuleFile(9, new File(['pdf'], 'lesson.pdf', { type: 'application/pdf' }));
  assert.equal(captured.url, '/professor/ai-learning-modules/9/files');
  assert.deepEqual([...captured.data.keys()], ['file']);
  await professor.deleteModuleFile(3);
  assert.equal(captured.method, 'delete');
  assert.equal(captured.url, '/professor/ai-learning-modules/files/3');
});

test('role menus do not offer routes forbidden by SecurityConfig', async () => {
  const navigation = await server.ssrLoadModule('/src/data/navigationData.js');
  assert.ok(navigation.SUPERADMIN_NAV_ITEMS.every((item) => item.path.startsWith('/superadmin')));
  assert.ok(!navigation.ADMIN_NAV_ITEMS.some((item) => item.path === '/admin/subjects'));
});

test('401 from an old token does not clear a newer session; current token expiry does', async () => {
  const reject = api.interceptors.response.handlers[0].rejected;
  localStorage.setItem('halo_token', 'new-token');
  await assert.rejects(reject({ response: { status: 401 }, config: { headers: { Authorization: 'Bearer old-token' } } }));
  assert.equal(localStorage.getItem('halo_token'), 'new-token');
  let notified = false;
  window.addEventListener('halo:session-expired', () => { notified = true; }, { once: true });
  await assert.rejects(reject({ response: { status: 401 }, config: { headers: { Authorization: 'Bearer new-token' } } }));
  assert.equal(localStorage.getItem('halo_token'), null);
  assert.equal(notified, true);
});


test('mentor open uses POST with bearer token, actual module ID and cancellation', async () => {
  localStorage.setItem('halo_user', JSON.stringify({ name: 'Student', email: 'student@example.test', role: 'student' }));
  localStorage.setItem('halo_token', 'mentor-test-token');
  const signal = new AbortController().signal;
  await mentor.openSession(15, { signal });
  assert.equal(captured.method, 'post');
  assert.equal(captured.url, '/student/mentor/open/15');
  assert.equal(captured.headers.Authorization, 'Bearer mentor-test-token');
  assert.equal(captured.signal, signal);
  await assert.rejects(mentor.openSession(undefined), /valid module ID/);
  await assert.rejects(mentor.openSession(0), /valid module ID/);
});

```

## frontend/tests/frontend.test.mjs

```javascript
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { parseLoginResponse, findAvailableWeek, buildAssessmentAnswers } from '../src/utils/backendContract.js';
import { readSession } from '../src/utils/session.js';
import { mapWithConcurrency } from '../src/utils/asyncPool.js';

const contract = JSON.parse(readFileSync(new URL('./fixtures/backend-contract.json', import.meta.url)));
const walk = (directory) => readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
  const file = path.join(directory, entry.name);
  return entry.isDirectory() ? walk(file) : [file];
});
const normalize = (url) => url.replace(/\$\{[^}]+\}|\{[^}]+\}/g, ':id');

test('every service URL and HTTP verb exists in the supplied Java controllers', () => {
  let count = 0;
  for (const file of walk('src/services').filter((file) => file.endsWith('.js'))) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(/apiClient\.(get|post|put|patch|delete)\(['"`]([^'"`]+)['"`]/g)) {
      count++;
      const [, method, url] = match;
      assert.ok(contract.endpoints.some((endpoint) => endpoint.method === method.toUpperCase()
        && normalize(endpoint.path) === normalize('/api' + url)), `${file}: ${method} ${url}`);
    }
  }
  assert.ok(count > 50, 'the scan must cover all service layers');
});

test('login validates the flat response and maps all four backend roles', () => {
  for (const [apiRole, expected] of Object.entries({ ADMIN: 'admin', SUPER_ADMIN: 'superadmin', PROFESSOR: 'professor', STUDENT: 'student' })) {
    const session = parseLoginResponse({ name: 'Test', email: 'test@example.test', role: apiRole, token: 'test-token' });
    assert.equal(session.user.role, expected);
  }
  assert.throws(() => parseLoginResponse({ name: 'Test', email: 'test@example.test', role: 'ADMIN' }));
  assert.throws(() => parseLoginResponse({ name: 'Test', email: 'test@example.test', role: 'OWNER', token: 'test' }));
});

test('restoration requires a user and token from the same storage', () => {
  const user = JSON.stringify({ name: 'Test', email: 'test@example.test', role: 'student' });
  const storage = (values) => ({ getItem: (key) => values[key] ?? null });
  assert.equal(readSession([storage({ halo_user: user }), storage({ halo_token: 'test' })]).user, null);
  assert.equal(readSession([storage({ halo_user: '{bad', halo_token: 'old' }), storage({ halo_user: user, halo_token: 'valid' })]).token, 'valid');
  assert.equal(readSession([storage({ halo_user: user, halo_token: '' })]).user, null);
});

test('next lesson ignores unpublished, completed and locked weeks and sorts by week number', () => {
  const weeks = [
    { weekId: 4, weekNumber: 4, unlocked: true, lessonAvailable: true, completed: false },
    { weekId: 1, weekNumber: 1, unlocked: true, lessonAvailable: false, completed: false },
    { weekId: 3, weekNumber: 3, unlocked: true, lessonAvailable: true, completed: false },
    { weekId: 2, weekNumber: 2, unlocked: false, lessonAvailable: true, completed: false },
    { weekId: 0, weekNumber: 0, unlocked: true, lessonAvailable: true, completed: true },
  ];
  assert.equal(findAvailableWeek(weeks).weekId, 3);
  assert.equal(weeks[0].weekId, 4, 'input should not be mutated');
  assert.equal(findAvailableWeek([weeks[1], weeks[3], weeks[4]]), undefined);
});

test('assessment payload uses numeric question IDs and valid letters for every question', () => {
  const questions = [{ id: 41 }, { id: 42 }];
  assert.deepEqual(buildAssessmentAnswers(questions, { 41: 'A', 42: 'D' }), [
    { questionId: 41, answer: 'A' }, { questionId: 42, answer: 'D' },
  ]);
  assert.throws(() => buildAssessmentAnswers(questions, { 41: 'A' }));
  assert.throws(() => buildAssessmentAnswers(questions, { 41: 0, 42: 'D' }));
  assert.throws(() => buildAssessmentAnswers([], {}));
  assert.equal(contract.dtos.StudentAnswerRequest.answers, 'List<AnswerItem>');
});

test('progress requests have bounded concurrency, preserve order and stop on abort', async () => {
  let active = 0;
  let peak = 0;
  const result = await mapWithConcurrency([1, 2, 3, 4, 5, 6], 2, async (item) => {
    peak = Math.max(peak, ++active);
    await new Promise((resolve) => setTimeout(resolve, 5));
    active--;
    return item * 2;
  });
  assert.equal(peak, 2);
  assert.deepEqual(result, [2, 4, 6, 8, 10, 12]);
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(mapWithConcurrency([1], 2, () => assert.fail('must not start'), controller.signal));
});

test('all statically referenced CSS module classes exist', () => {
  for (const file of walk('src').filter((file) => file.endsWith('.jsx'))) {
    const source = readFileSync(file, 'utf8');
    const cssImport = source.match(/import styles from ['"]([^'"]+)['"]/);
    if (!cssImport) continue;
    const css = readFileSync(path.resolve(path.dirname(file), cssImport[1]), 'utf8');
    for (const [, selector] of source.matchAll(/styles\.([A-Za-z_]\w*)/g)) {
      assert.match(css, new RegExp('\\.' + selector + '(?![\\w-])'), `${file}: ${selector}`);
    }
  }
});


test('lesson errors distinguish access denial from AI provider and authentication failures', async () => {
  const { lessonErrorMessage } = await import('../src/utils/lessonErrors.js');
  assert.match(lessonErrorMessage({ response: { status: 403, data: { code: 'STUDENT_NOT_ENROLLED' } } }), /year level/);
  assert.match(lessonErrorMessage({ response: { status: 401 } }), /sign in/);
  assert.match(lessonErrorMessage({ response: { status: 502 } }), /still available/);
  assert.match(lessonErrorMessage({ response: { status: 405, data: { code: 'METHOD_NOT_ALLOWED' } } }), /method/);
});

```

## reference/apiClient.js

```javascript
import axios from 'axios';
import { clearSession, readSession } from '../utils/session';

// VITE_API_URL must include /api; same-origin deployments use /api by default.
const apiClient = axios.create({ baseURL: import.meta.env.VITE_API_URL || '/api' });

apiClient.interceptors.request.use((config) => {
  const { token } = readSession();
  if (token && !config.skipAuth) config.headers.Authorization = 'Bearer ' + token;
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const sentToken = error.config?.headers?.Authorization;
    const { token } = readSession();
    // An old request or an invalid password on a public form must not
    // invalidate a newer authenticated session.
    if (error.response?.status === 401 && token && sentToken === 'Bearer ' + token) {
      clearSession();
      window.dispatchEvent(new Event('halo:session-expired'));
    }
    return Promise.reject(error);
  },
);

export default apiClient;

```
