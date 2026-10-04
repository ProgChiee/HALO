package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.AssessmentAttemptEntity;
import com.ptc.halo.entity.StudentBadgeEntity;
import com.ptc.halo.entity.StudentProfileEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.Role;
import com.ptc.halo.repository.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;

@Transactional(readOnly = true)
@Service
public class ProfessorStudentService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentModuleProgressRepository studentModuleProgressRepository;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final StudentSubjectService studentSubjectService;

    public ProfessorStudentService(
            UserRepository userRepository,
            StudentProfileRepository studentProfileRepository, StudentModuleProgressRepository studentModuleProgressRepository, AssessmentAttemptRepository assessmentAttemptRepository, StudentBadgeRepository studentBadgeRepository, StudentSubjectService studentSubjectService) {

        this.userRepository = userRepository;
        this.studentProfileRepository =
                studentProfileRepository;
        this.studentModuleProgressRepository = studentModuleProgressRepository;
        this.assessmentAttemptRepository = assessmentAttemptRepository;
        this.studentBadgeRepository = studentBadgeRepository;
        this.studentSubjectService = studentSubjectService;
    }


    public List<ProfessorStudentResponse>
    getStudents(UserEntity professor) {
        requireProfessor(professor);

        List<UserEntity> students =
                userRepository.findByRole(Role.STUDENT);

        List<ProfessorStudentResponse> responses =
                new ArrayList<>();


        for (UserEntity student : students) {

            StudentProfileEntity profile =
                    studentProfileRepository
                            .findByUserId(student.getId())
                            .orElse(null);




            ProfessorStudentResponse response =
                    new ProfessorStudentResponse();

            response.setUserId(
                    student.getId()
            );

            response.setName(
                    student.getName()
            );

            response.setEmail(
                    student.getEmail()
            );

            response.setStudentId(
                    profile == null ? null : profile.getStudentId()
            );

            response.setSection(
                    profile == null ? null : profile.getSection()
            );

            response.setYearLevel(
                    profile == null ? null : profile.getYearLevel()
            );


            responses.add(response);
        }


        return responses;
    }
    public ProfessorStudentProgressResponse
    getStudentProgress(Long userId, UserEntity professor) {
        requireProfessor(professor);

        UserEntity student = requireStudent(userId);

        StudentProfileEntity profile =
                studentProfileRepository
                        .findByUserId(student.getId())
                        .orElse(null);


        long completedModules =
                studentModuleProgressRepository
                        .countByStudentIdAndCompletedTrueAndModule_Week_Subject_Professor_User_Id(
                                student.getId(), professor.getId()
                        );

        long passedAssessments =
                assessmentAttemptRepository
                        .countByStudentIdAndPassedTrueAndAssessment_Module_Week_Subject_Professor_User_Id(
                                student.getId(), professor.getId()
                        );

        long totalBadges =
                studentBadgeRepository
                        .countByStudentId(
                                student.getId()
                        );


        ProfessorStudentProgressResponse response =
                new ProfessorStudentProgressResponse();

        response.setUserId(
                student.getId()
        );

        response.setStudentId(
                profile == null ? null : profile.getStudentId()
        );

        response.setName(
                student.getName()
        );

        response.setEmail(
                student.getEmail()
        );

        response.setSection(
                profile == null ? null : profile.getSection()
        );

        response.setCompletedModules(
                completedModules
        );

        response.setPassedAssessments(
                passedAssessments
        );

        response.setTotalBadges(
                totalBadges
        );

        return response;
    }
    public List<StudentSubjectResponse>
    getStudentSubjects(Long userId, UserEntity professor) {
        requireProfessor(professor);

        UserEntity student = requireStudent(userId);

        return studentSubjectService
                .getProfessorStudentSubjects(student, professor);
    }
    public List<ProfessorStudentAssessmentResponse>
    getStudentAssessmentHistory(Long userId, UserEntity professor) {
        requireProfessor(professor);

        UserEntity student = requireStudent(userId);


        List<AssessmentAttemptEntity> attempts =
                assessmentAttemptRepository
                        .findByStudentIdAndAssessment_Module_Week_Subject_Professor_User_IdAndSubmittedAtIsNotNullOrderBySubmittedAtDesc(
                                student.getId(), professor.getId()
                        );


        List<ProfessorStudentAssessmentResponse> responses =
                new ArrayList<>();


        for (AssessmentAttemptEntity attempt : attempts) {

            ProfessorStudentAssessmentResponse response =
                    new ProfessorStudentAssessmentResponse();


            response.setAttemptId(
                    attempt.getId()
            );


            response.setAssessmentTitle(
                    attempt.getAssessment()
                            .getTitle()
            );


            response.setWeekNumber(
                    attempt.getAssessment()
                            .getModule()
                            .getWeek()
                            .getWeekNumber()
            );


            response.setSubjectName(
                    attempt.getAssessment()
                            .getModule()
                            .getWeek()
                            .getSubject()
                            .getSubjectName()
            );


            response.setScore(
                    attempt.getScore()
            );


            response.setPassed(
                    attempt.getPassed()
            );


            response.setStartedAt(
                    attempt.getStartedAt()
            );


            response.setSubmittedAt(
                    attempt.getSubmittedAt()
            );


            responses.add(response);
        }


        return responses;
    }
    public List<ProfessorStudentBadgeResponse>
    getStudentBadges(Long userId, UserEntity professor) {
        requireProfessor(professor);

        UserEntity student = requireStudent(userId);


        List<StudentBadgeEntity> badges =
                studentBadgeRepository
                        .findByStudentIdOrderByEarnedAtDesc(
                                student.getId()
                        );


        List<ProfessorStudentBadgeResponse> responses =
                new ArrayList<>();


        for (StudentBadgeEntity badge : badges) {

            ProfessorStudentBadgeResponse response =
                    new ProfessorStudentBadgeResponse();

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

            responses.add(response);
        }


        return responses;
    }
    private UserEntity requireStudent(Long id) {
        if (id == null || id <= 0) throw notFound();
        return userRepository.findById(id).filter(user -> user.getRole() == Role.STUDENT)
                .orElseThrow(this::notFound);
    }
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
    private void requireProfessor(UserEntity professor) {
        if (professor == null || professor.getId() == null || professor.getRole() != Role.PROFESSOR)
            throw new AccessDeniedException("Professor access required");
    }
}
