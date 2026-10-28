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
    private final StudentSubjectEnrollmentRepository enrollments;

    public StudentLessonAccessService(UserRepository users, StudentProfileRepository profiles, StudentSubjectEnrollmentRepository enrollments) {
        this.users = users;
        this.profiles = profiles;
        this.enrollments = enrollments;
    }

    public UserEntity requireStudent(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
        }
        UserEntity student = com.ptc.halo.security.AuthenticatedAccount.find(users, authentication)
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
        // Academic eligibility and explicit enrollment are independent requirements.
        if (profile.getYearLevel() == null || profile.getYearLevel() != subject.getYearLevel()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "STUDENT_NOT_ENROLLED");
        }
        if (!enrollments.existsByStudent_IdAndSubject_Id(student.getId(), subject.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ENROLLMENT_REQUIRED");
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
