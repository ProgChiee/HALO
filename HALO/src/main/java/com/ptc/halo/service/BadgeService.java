package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.StudentBadgeResponse;
import com.ptc.halo.entity.AssessmentAttemptEntity;
import com.ptc.halo.entity.StudentBadgeEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.BadgeType;
import com.ptc.halo.repository.AssessmentAttemptRepository;
import com.ptc.halo.repository.StudentBadgeRepository;
import com.ptc.halo.repository.StudentModuleProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ptc.halo.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BadgeService {
    private final UserRepository userRepository;

    private final StudentBadgeRepository studentBadgeRepository;
    private final ActivityLogService activityLogService;
    private final StudentModuleProgressRepository studentModuleProgressRepository;

    private final AssessmentAttemptRepository
            assessmentAttemptRepository;

    public BadgeService(
            StudentBadgeRepository studentBadgeRepository, ActivityLogService activityLogService,
            StudentModuleProgressRepository studentModuleProgressRepository,
            AssessmentAttemptRepository assessmentAttemptRepository, UserRepository userRepository) {
        this.userRepository = userRepository;

        this.studentBadgeRepository =
                studentBadgeRepository;
        this.activityLogService = activityLogService;

        this.studentModuleProgressRepository =
                studentModuleProgressRepository;

        this.assessmentAttemptRepository =
                assessmentAttemptRepository;
    }


    @Transactional
    public void checkAndAwardBadges(
            UserEntity student,
            AssessmentAttemptEntity currentAttempt) {
        checkAndAwardBadges(student, currentAttempt, null);
    }
    @Transactional
    public void checkAndAwardBadges(UserEntity student, AssessmentAttemptEntity currentAttempt, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        // Reuse the assessment lifecycle lock before any award reads. The surrounding
        // transaction retains it through commit, including badge and audit writes.
        userRepository.findForAssessmentLifecycle(student.getId()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
        checkProgressBadges(student, acting);
        if (studentModuleProgressRepository.countEligibleCompletedModules(student.getId()) > 0) {
            awardBadge(student, acting, BadgeType.MODULE_FINISHER, "Module Finisher",
                    "Fully completed an available learning module.");
        }
        if (studentModuleProgressRepository.countMasteredEligibleSubjects(student.getId()) > 0) {
            awardBadge(student, acting, BadgeType.SUBJECT_MASTER, "Subject Master",
                    "Completed every published, available module in one subject.");
        }

        checkAssessmentBadges(
                student, acting,
                currentAttempt
        );

        checkHaloAchiever(student, acting);
    }


    // =========================================
    // PROGRESS BADGES
    // =========================================

    private void checkProgressBadges(
            UserEntity student, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        long completedModules =
                studentModuleProgressRepository
                        .countByStudentIdAndCompletedTrue(
                                student.getId()
                        );


        if (completedModules >= 1) {

            awardBadge(
                    student, acting,
                    BadgeType.FIRST_STEP,
                    "First Step",
                    "Completed your first learning module."
            );
        }


        if (completedModules >= 3) {

            awardBadge(
                    student, acting,
                    BadgeType.GETTING_STARTED,
                    "Getting Started",
                    "Completed 3 learning modules."
            );
        }


        if (completedModules >= 5) {

            awardBadge(
                    student, acting,
                    BadgeType.KNOWLEDGE_SEEKER,
                    "Knowledge Seeker",
                    "Completed 5 learning modules."
            );
        }


        if (completedModules >= 10) {

            awardBadge(
                    student, acting,
                    BadgeType.DEDICATED_LEARNER,
                    "Dedicated Learner",
                    "Completed 10 learning modules."
            );
        }
    }


    // =========================================
    // ASSESSMENT BADGES
    // =========================================

    private void checkAssessmentBadges(
            UserEntity student, com.ptc.halo.entity.AdminActingSessionEntity acting,
            AssessmentAttemptEntity currentAttempt) {


        // PERFECT SCORE
        if (currentAttempt.getScore() != null &&
                currentAttempt.getScore() == 100) {

            awardBadge(
                    student, acting,
                    BadgeType.PERFECT_SCORE,
                    "Perfect Score",
                    "Earned a perfect score on an assessment."
            );
        }


        List<AssessmentAttemptEntity> attempts =
                assessmentAttemptRepository
                        .findByStudentIdAndAssessmentIdOrderByStartedAtDesc(
                                student.getId(),
                                currentAttempt
                                        .getAssessment()
                                        .getId()
                        );


        // FIRST TRY
        if (Boolean.TRUE.equals(
                currentAttempt.getPassed()
        )) {

            long submittedAttempts =
                    attempts.stream()
                            .filter(attempt ->
                                    attempt.getSubmittedAt()
                                            != null
                            )
                            .count();

            if (submittedAttempts == 1) {

                awardBadge(
                        student, acting,
                        BadgeType.FIRST_TRY,
                        "First Try",
                        "Passed an assessment on the first attempt."
                );
            }
        }


        // NEVER GIVE UP
        if (Boolean.TRUE.equals(
                currentAttempt.getPassed()
        )) {

            boolean previouslyFailed =
                    attempts.stream()
                            .anyMatch(attempt ->
                                    attempt.getId()
                                            .equals(
                                                    currentAttempt.getId()
                                            ) == false
                                            &&
                                            attempt.getSubmittedAt()
                                                    != null
                                            &&
                                            Boolean.FALSE.equals(
                                                    attempt.getPassed()
                                            )
                            );

            if (previouslyFailed) {

                awardBadge(
                        student, acting,
                        BadgeType.NEVER_GIVE_UP,
                        "Never Give Up",
                        "Passed an assessment after a previous failed attempt."
                );
            }
        }


        // COMEBACK STRONGER
        checkComebackBadge(
                student, acting,
                currentAttempt,
                attempts
        );


        // ASSESSMENT ACE
        long passedAssessments =
                assessmentAttemptRepository
                        .countByStudentIdAndPassedTrue(
                                student.getId()
                        );

        if (passedAssessments >= 5) {

            awardBadge(
                    student, acting,
                    BadgeType.ASSESSMENT_ACE,
                    "Assessment Ace",
                    "Passed 5 assessments."
            );
        }
    }


    // =========================================
    // COMEBACK STRONGER
    // =========================================

    private void checkComebackBadge(
            UserEntity student, com.ptc.halo.entity.AdminActingSessionEntity acting,
            AssessmentAttemptEntity currentAttempt,
            List<AssessmentAttemptEntity> attempts) {

        if (currentAttempt.getScore() == null) {
            return;
        }


        AssessmentAttemptEntity previousAttempt =
                attempts.stream()
                        .filter(attempt ->
                                !attempt.getId()
                                        .equals(
                                                currentAttempt.getId()
                                        )
                        )
                        .filter(attempt ->
                                attempt.getSubmittedAt()
                                        != null
                        )
                        .filter(attempt ->
                                attempt.getScore()
                                        != null
                        )
                        .findFirst()
                        .orElse(null);


        if (previousAttempt == null) {
            return;
        }


        int improvement =
                currentAttempt.getScore()
                        - previousAttempt.getScore();

        if (improvement >= 20) {

            awardBadge(
                    student, acting,
                    BadgeType.COMEBACK_STRONGER,
                    "Comeback Stronger",
                    "Improved your assessment score by at least 20 points."
            );
        }
    }


    // =========================================
    // HALO ACHIEVER
    // =========================================

    private void checkHaloAchiever(
            UserEntity student, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        long totalBadges = studentBadgeRepository.findByStudentIdOrderByEarnedAtDesc(student.getId())
                .stream().map(StudentBadgeEntity::getBadgeType)
                .filter(type -> type != null && type != BadgeType.HALO_ACHIEVER).distinct().count();

        if (totalBadges >= 10) {

            awardBadge(
                    student, acting,
                    BadgeType.HALO_ACHIEVER,
                    "HALO Achiever",
                    "Earned 10 distinct ordinary HALO badges."
            );
        }
    }


    // =========================================
    // SAVE BADGE
    // =========================================

    private void awardBadge(
            UserEntity student, com.ptc.halo.entity.AdminActingSessionEntity acting,
            BadgeType badgeType,
            String badgeName,
            String description) {

        boolean alreadyEarned =
                studentBadgeRepository
                        .existsByStudentIdAndBadgeType(
                                student.getId(),
                                badgeType
                        );


        if (alreadyEarned) {
            return;
        }


        StudentBadgeEntity badge =
                new StudentBadgeEntity();

        badge.setStudent(student);

        badge.setBadgeType(
                badgeType
        );

        badge.setBadgeName(
                badgeName
        );

        badge.setDescription(
                description
        );

        badge.setEarnedAt(
                LocalDateTime.now()
        );


        studentBadgeRepository.save(badge);

        writeStudentAudit(
                student, acting,
                ActivityType.BADGE,
                "Earned badge: " + badge.getBadgeName());
    }
    public List<StudentBadgeResponse> getStudentBadges(
            UserEntity student) {

        return studentBadgeRepository
                .findByStudentIdOrderByEarnedAtDesc(
                        student.getId()
                )
                .stream()
                .map(badge -> {

                    StudentBadgeResponse response =
                            new StudentBadgeResponse();

                    response.setId(
                            badge.getId()
                    );

                    response.setBadgeType(
                            badge.getBadgeType()
                    );

                    response.setBadgeName(
                            badge.getBadgeName()
                    );

                    response.setDescription(
                            badge.getDescription()
                    );

                    response.setEarnedAt(
                            badge.getEarnedAt()
                    );

                    return response;
                })
                .toList();
    }

    private void writeStudentAudit(UserEntity student, com.ptc.halo.entity.AdminActingSessionEntity acting, ActivityType type, String action) {
        if (acting == null) activityLogService.createLog(student,type,action);
        else activityLogService.createStudentLog(student,acting,type,action);
    }
}
