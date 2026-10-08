package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.StudentAnswerRequest;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.AssessmentStatus;
import com.ptc.halo.enums.LessonStatus;
import com.ptc.halo.repository.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final AssessmentQuestionRepository questionRepository;
    private final AiLearningModuleRepository moduleRepository;
    private final AssessmentAiService assessmentAiService;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final StudentAnswerRepository studentAnswerRepository;
    private final StudentModuleProgressRepository studentModuleProgressRepository;
    private final BadgeService badgeService;
    private final ActivityLogService activityLogService;
    private final StudentLearningProgressionService progressionService;
    private final UserRepository userRepository;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            AssessmentQuestionRepository questionRepository,
            AiLearningModuleRepository moduleRepository,
            AssessmentAiService assessmentAiService, AssessmentAttemptRepository assessmentAttemptRepository, StudentAnswerRepository studentAnswerRepository, StudentModuleProgressRepository studentModuleProgressRepository, BadgeService badgeService, ActivityLogService activityLogService, StudentLearningProgressionService progressionService, UserRepository userRepository) {

        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.moduleRepository = moduleRepository;
        this.assessmentAiService = assessmentAiService;
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.studentAnswerRepository = studentAnswerRepository;
        this.studentModuleProgressRepository = studentModuleProgressRepository;
        this.badgeService = badgeService;
        this.activityLogService = activityLogService;
        this.progressionService = progressionService;
        this.userRepository = userRepository;
    }

    @Transactional
    public AssessmentEntity generateAssessment(Long moduleId) {

        AiLearningModuleEntity module =
                moduleRepository.findById(moduleId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Learning module not found"
                                )
                        );

        if (module.getStatus() != LessonStatus.APPROVED) {
            throw new RuntimeException(
                    "Assessment can only be generated from an approved lesson"
            );
        }

        if (assessmentRepository
                .findByModuleId(moduleId)
                .isPresent()) {

            throw new RuntimeException(
                    "Assessment already exists for this module"
            );
        }

        return persistGeneratedAssessment(module, prepareAssessment(module));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public GeneratedAssessment prepareAssessment(AiLearningModuleEntity module) {
        return
                assessmentAiService.generateAssessment(
                        module.getLessonText(),
                        module.getGeneratedObjectives(),
                        module.getGeneratedKnowledge(),
                        module.getGeneratedExamples(),
                        module.getGeneratedSummary()
                );

    }

    @Transactional
    public AssessmentEntity persistGeneratedAssessment(AiLearningModuleEntity module, GeneratedAssessment generated) {
        if (module.getStatus() != LessonStatus.APPROVED)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "MODULE_GENERATION_REQUIRED");
        if (assessmentRepository.findByModuleId(module.getId()).isPresent())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "RESOURCE_CONFLICT");
        if (generated == null ||
                generated.getQuestions() == null ||
                generated.getQuestions().isEmpty()) {

            throw new RuntimeException(
                    "AI did not generate any assessment questions"
            );
        }

        AssessmentEntity assessment =
                new AssessmentEntity();

        assessment.setModule(module);
        assessment.setTitle("Module Assessment");
        assessment.setPassingScore(70);
        assessment.setStatus(AssessmentStatus.AVAILABLE);
        assessment.setCreatedAt(LocalDateTime.now());

        AssessmentEntity savedAssessment =
                assessmentRepository.save(assessment);

        for (GeneratedAssessmentQuestion generatedQuestion
                : generated.getQuestions()) {

            AssessmentQuestionEntity question =
                    new AssessmentQuestionEntity();

            question.setAssessment(savedAssessment);

            question.setQuestionNumber(
                    generatedQuestion.getQuestionNumber()
            );

            question.setQuestionText(
                    generatedQuestion.getQuestion()
            );

            question.setOptionA(
                    generatedQuestion.getOptionA()
            );

            question.setOptionB(
                    generatedQuestion.getOptionB()
            );

            question.setOptionC(
                    generatedQuestion.getOptionC()
            );

            question.setOptionD(
                    generatedQuestion.getOptionD()
            );

            question.setCorrectAnswer(
                    generatedQuestion.getCorrectAnswer()
            );

            questionRepository.save(question);
        }

        return savedAssessment;
    }

    @Transactional(readOnly = true)
    public AssessmentResponse getAssessment(
            Long moduleId,
            UserEntity student) {

        progressionService.validateModuleAccess(
                moduleId,
                student
        );

        AssessmentEntity assessment =
                assessmentRepository.findByModuleId(moduleId)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "ASSESSMENT_NOT_FOUND")
                        );

        if (assessment.getStatus() != AssessmentStatus.AVAILABLE) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "ASSESSMENT_NOT_AVAILABLE");
        }

        List<AssessmentQuestionResponse> questions =
                assessment.getQuestions()
                        .stream()
                        .map(question -> {

                            AssessmentQuestionResponse response =
                                    new AssessmentQuestionResponse();

                            response.setId(question.getId());
                            response.setQuestionNumber(
                                    question.getQuestionNumber()
                            );
                            response.setQuestionText(
                                    question.getQuestionText()
                            );
                            response.setOptionA(
                                    question.getOptionA()
                            );
                            response.setOptionB(
                                    question.getOptionB()
                            );
                            response.setOptionC(
                                    question.getOptionC()
                            );
                            response.setOptionD(
                                    question.getOptionD()
                            );

                            return response;
                        })
                        .toList();

        AssessmentResponse response =
                new AssessmentResponse();

        response.setId(assessment.getId());
        response.setTitle(assessment.getTitle());
        response.setPassingScore(
                assessment.getPassingScore()
        );
        response.setQuestions(questions);

        return response;
    }
    @Transactional
    public AssessmentAttemptEntity startAttempt(
            Long moduleId,
            UserEntity student) {

        lockStudentLifecycle(student);
        progressionService.validateModuleAccess(
                moduleId,
                student
        );

        AssessmentEntity assessment =
                assessmentRepository.findByModuleId(moduleId)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "ASSESSMENT_NOT_FOUND")
                        );

        if (assessment.getStatus() != AssessmentStatus.AVAILABLE) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "ASSESSMENT_NOT_AVAILABLE");
        }

        List<AssessmentAttemptEntity> attempts =
                assessmentAttemptRepository
                        .findByStudentIdAndAssessmentIdOrderByStartedAtDesc(
                                student.getId(),
                                assessment.getId()
                        );

        // Check if student already passed
        boolean alreadyPassed =
                attempts.stream()
                        .anyMatch(attempt ->
                                Boolean.TRUE.equals(
                                        attempt.getPassed()
                                )
                        );

        if (alreadyPassed) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "ASSESSMENT_ALREADY_PASSED");
        }

        // Resume unfinished attempt
        for (AssessmentAttemptEntity attempt : attempts) {

            if (attempt.getSubmittedAt() == null) {
                return attempt;
            }
        }

        // No unfinished attempt -> create new attempt
        AssessmentAttemptEntity attempt =
                new AssessmentAttemptEntity();

        attempt.setAssessment(assessment);
        attempt.setStudent(student);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setSubmittedAt(null);
        attempt.setScore(0);
        attempt.setPassed(false);

        return assessmentAttemptRepository.save(attempt);
    }
    @Transactional
    public AssessmentAttemptEntity submitAttempt(
            Long attemptId,
            UserEntity student,
            StudentAnswerRequest request) {

        lockStudentLifecycle(student);
        AssessmentAttemptEntity attempt =
                assessmentAttemptRepository.findById(attemptId)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND")
                        );

        if (!attempt.getStudent().getId().equals(student.getId())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        }

        if (attempt.getSubmittedAt() != null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "ASSESSMENT_ALREADY_SUBMITTED");
        }

        // Eligibility can change after an attempt starts. Recheck before accepting answers.
        AssessmentEntity assessment = attempt.getAssessment();
        progressionService.validateModuleAccess(assessment.getModule().getId(), student);
        if (assessment.getStatus() != AssessmentStatus.AVAILABLE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "ASSESSMENT_NOT_AVAILABLE");
        }

        // Validate the whole set before writing any answers or awarding progress/badges.
        var expected = questionRepository.findByAssessmentIdOrderByQuestionNumberAsc(attempt.getAssessment().getId());
        var expectedIds = expected.stream().map(AssessmentQuestionEntity::getId).collect(java.util.stream.Collectors.toSet());
        if (expectedIds.isEmpty() || request == null || request.getAnswers() == null
                || request.getAnswers().size() != expectedIds.size()) throw invalidAnswerSet();
        var submitted = new java.util.HashMap<Long, String>();
        for (var item : request.getAnswers()) {
            if (item == null || item.getQuestionId() == null || !expectedIds.contains(item.getQuestionId())
                    || item.getAnswer() == null) throw invalidAnswerSet();
            String answer = item.getAnswer().trim().toUpperCase(java.util.Locale.ROOT);
            if (!answer.matches("[ABCD]") || submitted.putIfAbsent(item.getQuestionId(), answer) != null)
                throw invalidAnswerSet();
        }
        if (!submitted.keySet().equals(expectedIds)) throw invalidAnswerSet();

        int correctAnswers = 0;
        var answers = new ArrayList<StudentAnswerEntity>();
        for (var question : expected) {
            String studentAnswer = submitted.get(question.getId());
            var answer = new StudentAnswerEntity();
            answer.setAttempt(attempt);
            answer.setQuestion(question);
            answer.setAnswer(studentAnswer);
            answers.add(answer);
            if (question.getCorrectAnswer().equalsIgnoreCase(studentAnswer)) correctAnswers++;
        }
        try {
            studentAnswerRepository.saveAllAndFlush(answers);
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            for (Throwable cause = conflict; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException constraint
                        && constraint.getConstraintName() != null
                        && constraint.getConstraintName().toLowerCase(java.util.Locale.ROOT).contains("uk_student_answer_attempt_question"))
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "ANSWERS_ALREADY_RECORDED");
            }
            throw conflict;
        }
        int totalQuestions = expectedIds.size();

        int score =
                (int) Math.round(
                        ((double) correctAnswers
                                / totalQuestions) * 100
                );

        boolean passed =
                score >= attempt.getAssessment()
                        .getPassingScore();

        attempt.setScore(score);
        attempt.setPassed(passed);
        attempt.setSubmittedAt(LocalDateTime.now());

        if (passed) {

            AiLearningModuleEntity module =
                    attempt.getAssessment().getModule();

            StudentModuleProgressEntity progress =
                    studentModuleProgressRepository
                            .findByStudentIdAndModuleId(
                                    student.getId(),
                                    module.getId()
                            )
                            .orElseGet(() -> {

                                StudentModuleProgressEntity newProgress =
                                        new StudentModuleProgressEntity();

                                newProgress.setStudent(student);
                                newProgress.setModule(module);

                                return newProgress;
                            });

            progress.setCompleted(true);
            progress.setCompletedAt(LocalDateTime.now());

            studentModuleProgressRepository.save(progress);
            
            activityLogService.createLog(
                    student,
                    ActivityType.PROGRESS,
                    "Completed learning module ID " + module.getId()
            );
        }

        AssessmentAttemptEntity savedAttempt =
                assessmentAttemptRepository.save(attempt);


        if (passed) {

            activityLogService.createLog(
                    student,
                    ActivityType.ASSESSMENT,
                    "Passed module assessment with score " + score + "%"
            );

        } else {

            activityLogService.createLog(
                    student,
                    ActivityType.ASSESSMENT,
                    "Failed module assessment with score " + score + "%"
            );
        }


        badgeService.checkAndAwardBadges(
                student,
                savedAttempt
        );

        return savedAttempt;

    }

    private void lockStudentLifecycle(UserEntity student) {
        // First database operation: serialize starts/submits (including shared progress/badges)
        // for this Student across application instances until the transaction completes.
        userRepository.findForAssessmentLifecycle(student.getId()).orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private org.springframework.web.server.ResponseStatusException invalidAnswerSet() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "INVALID_ANSWER_SET");
    }

    public List<AssessmentAttemptHistoryResponse> getAttemptHistory(
            Long moduleId,
            UserEntity student) {

        progressionService.validateModuleAccess(
                moduleId,
                student
        );

        AssessmentEntity assessment =
                assessmentRepository.findByModuleId(moduleId)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "ASSESSMENT_NOT_FOUND")
                        );

        List<AssessmentAttemptEntity> attempts =
                assessmentAttemptRepository
                        .findByStudentIdAndAssessmentIdOrderByStartedAtDesc(
                                student.getId(),
                                assessment.getId()
                        );

        return attempts.stream()
                .map(attempt -> {

                    AssessmentAttemptHistoryResponse response =
                            new AssessmentAttemptHistoryResponse();

                    response.setAttemptId(attempt.getId());
                    response.setScore(attempt.getScore());
                    response.setPassed(attempt.getPassed());
                    response.setStartedAt(attempt.getStartedAt());
                    response.setSubmittedAt(attempt.getSubmittedAt());

                    return response;
                })
                .toList();
    }
    @Transactional(readOnly = true)
    public AssessmentResultResponse getAttemptResult(
            Long attemptId,
            UserEntity student) {

        AssessmentAttemptEntity attempt =
                assessmentAttemptRepository.findById(attemptId)
                        .orElseThrow(() ->
                                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND")
                        );

        if (!attempt.getStudent().getId()
                .equals(student.getId())) {

            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "ACCESS_DENIED");
        }

        if (attempt.getSubmittedAt() == null) {

            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "ASSESSMENT_NOT_SUBMITTED");
        }

        List<StudentAnswerEntity> answers =
                studentAnswerRepository
                        .findByAttemptIdOrderByQuestionQuestionNumberAsc(
                                attemptId
                        );

        AssessmentResultResponse response = getAssessmentResultResponse(answers, attempt);

        return response;
    }

    @NotNull
    private static AssessmentResultResponse getAssessmentResultResponse(List<StudentAnswerEntity> answers, AssessmentAttemptEntity attempt) {
        List<AssessmentAnswerFeedbackResponse> feedback =
                new ArrayList<>();

        for (StudentAnswerEntity answer : answers) {

            AssessmentQuestionEntity question =
                    answer.getQuestion();

            AssessmentAnswerFeedbackResponse item =
                    new AssessmentAnswerFeedbackResponse();

            item.setQuestionId(question.getId());
            item.setQuestionNumber(
                    question.getQuestionNumber()
            );

            item.setQuestionText(
                    question.getQuestionText()
            );

            item.setStudentAnswer(
                    answer.getAnswer()
            );

            item.setCorrectAnswer(
                    question.getCorrectAnswer()
            );

            item.setCorrect(
                    question.getCorrectAnswer()
                            .equalsIgnoreCase(
                                    answer.getAnswer()
                            )
            );

            feedback.add(item);
        }

        AssessmentResultResponse response =
                new AssessmentResultResponse();

        response.setAttemptId(attempt.getId());
        response.setScore(attempt.getScore());
        response.setPassed(attempt.getPassed());
        response.setFeedback(feedback);
        return response;
    }
    public AssessmentStatusResponse getAssessmentStatus(
            Long moduleId,
            UserEntity student) {

        progressionService.validateModuleAccess(
                moduleId,
                student
        );

        AssessmentStatusResponse response =
                new AssessmentStatusResponse();

        response.setModuleId(moduleId);

        Optional<AssessmentEntity> assessmentOptional =
                assessmentRepository.findByModuleId(moduleId);

        if (assessmentOptional.isEmpty()) {

            response.setAssessmentExists(false);
            response.setAssessmentAvailable(false);
            response.setHasUnfinishedAttempt(false);
            response.setAlreadyPassed(false);
            response.setCanTakeAssessment(false);

            return response;
        }

        AssessmentEntity assessment =
                assessmentOptional.get();

        response.setAssessmentExists(true);

        boolean available =
                assessment.getStatus()
                        == AssessmentStatus.AVAILABLE;

        response.setAssessmentAvailable(available);

        List<AssessmentAttemptEntity> attempts =
                assessmentAttemptRepository
                        .findByStudentIdAndAssessmentIdOrderByStartedAtDesc(
                                student.getId(),
                                assessment.getId()
                        );

        boolean unfinished =
                attempts.stream()
                        .anyMatch(attempt ->
                                attempt.getSubmittedAt() == null
                        );

        boolean passed =
                attempts.stream()
                        .anyMatch(attempt ->
                                Boolean.TRUE.equals(
                                        attempt.getPassed()
                                )
                        );

        response.setHasUnfinishedAttempt(unfinished);
        response.setAlreadyPassed(passed);

        response.setCanTakeAssessment(
                available && !passed
        );

        return response;
    }
}
