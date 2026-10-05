package com.ptc.halo.service;

import com.ptc.halo.dtoResponse.StudentSubjectResponse;
import com.ptc.halo.entity.AiLearningModuleEntity;
import com.ptc.halo.entity.StudentProfileEntity;
import com.ptc.halo.entity.SubjectEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.enums.LessonStatus;
import com.ptc.halo.repository.AiLearningModuleRepository;
import com.ptc.halo.repository.StudentModuleProgressRepository;
import com.ptc.halo.repository.StudentProfileRepository;
import com.ptc.halo.repository.SubjectRepository;
import com.ptc.halo.repository.WeekRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class StudentSubjectService {

    private final SubjectRepository subjectRepository;

    private final StudentProfileRepository
            studentProfileRepository;

    private final WeekRepository weekRepository;

    private final AiLearningModuleRepository
            aiLearningModuleRepository;

    private final StudentModuleProgressRepository
            studentModuleProgressRepository;

    public StudentSubjectService(
            SubjectRepository subjectRepository,
            StudentProfileRepository studentProfileRepository,
            WeekRepository weekRepository,
            AiLearningModuleRepository aiLearningModuleRepository,
            StudentModuleProgressRepository studentModuleProgressRepository) {

        this.subjectRepository =
                subjectRepository;

        this.studentProfileRepository =
                studentProfileRepository;

        this.weekRepository =
                weekRepository;

        this.aiLearningModuleRepository =
                aiLearningModuleRepository;

        this.studentModuleProgressRepository =
                studentModuleProgressRepository;
    }


    public List<StudentSubjectResponse>
    getStudentSubjects(UserEntity student) {

        StudentProfileEntity profile =
                studentProfileRepository
                        .findByUserId(student.getId())
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Student profile not found"
                                )
                        );


        List<SubjectEntity> subjects =
                subjectRepository
                        .findByYearLevel(
                                profile.getYearLevel()
                        );


        return summarizeSubjects(student, subjects);
    }

    public List<StudentSubjectResponse> getProfessorStudentSubjects(UserEntity student, UserEntity professor) {
        var profile = studentProfileRepository.findByUserId(student.getId()).orElse(null);
        if (profile == null || profile.getYearLevel() == null) return List.of();
        var subjects = subjectRepository.findByProfessor_User_Id(professor.getId()).stream()
                .filter(subject -> subject.getYearLevel() == profile.getYearLevel()).toList();
        return summarizeSubjects(student, subjects);
    }

    private List<StudentSubjectResponse> summarizeSubjects(UserEntity student, List<SubjectEntity> subjects) {
        if (subjects.isEmpty()) return List.of();
        var rows = weekRepository.findStudentWeekRows(subjects.stream().map(SubjectEntity::getId).toList(), student.getId());
        var bySubject = rows.stream().collect(java.util.stream.Collectors.groupingBy(WeekRepository.StudentWeekRow::getSubjectId));
        List<StudentSubjectResponse> responses = new ArrayList<>();
        for (SubjectEntity subject : subjects) {
            var subjectRows = bySubject.getOrDefault(subject.getId(), List.of());
            int totalWeeks = subjectRows.size();
            int eligibleModuleCount = (int) subjectRows.stream().filter(row -> row.getModuleId() != null).count();
            int completedWeeks = (int) subjectRows.stream().filter(row -> row.getCompletedCount() > 0).count();
            int progressPercentage = 0;

            if (eligibleModuleCount > 0) {

                progressPercentage =
                        (int) Math.round(
                                ((double) completedWeeks
                                        / eligibleModuleCount)
                                        * 100
                        );
            }


            StudentSubjectResponse response =
                    new StudentSubjectResponse();
            response.setEligibleModuleCount(eligibleModuleCount);


            response.setSubjectId(
                    subject.getId()
            );

            response.setSubjectCode(
                    subject.getSubjectCode()
            );

            response.setSubjectName(
                    subject.getSubjectName()
            );

            response.setYearLevel(
                    subject.getYearLevel()
            );

            response.setTotalWeeks(
                    totalWeeks
            );

            response.setCompletedWeeks(
                    completedWeeks
            );

            response.setProgressPercentage(
                    progressPercentage
            );


            responses.add(response);
        }


        return responses;
    }
}
