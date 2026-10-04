package com.ptc.halo.service;

import com.ptc.halo.dtoRequest.SubjectRequest;
import com.ptc.halo.dtoRequest.SubjectUpdateRequest;
import com.ptc.halo.dtoRequest.WeekRequest;
import com.ptc.halo.dtoRequest.WeekUpdateRequest;
import com.ptc.halo.dtoResponse.SubjectResponse;
import com.ptc.halo.dtoResponse.WeekResponse;
import com.ptc.halo.entity.SubjectEntity;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.entity.WeekEntity;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.repository.SubjectRepository;
import com.ptc.halo.repository.WeekRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.ptc.halo.repository.ProfessorRepository;

import java.util.List;

@Service
public class ProfessorAcademicService {

    private final SubjectRepository subjectRepository;
    private final ProfessorRepository professorRepository;
    private final WeekRepository weekRepository;
    private final ActivityLogService activityLogService;

    public ProfessorAcademicService(
            SubjectRepository subjectRepository,
            WeekRepository weekRepository,
            ActivityLogService activityLogService, ProfessorRepository professorRepository) {
        this.professorRepository = professorRepository;

        this.subjectRepository = subjectRepository;
        this.weekRepository = weekRepository;
        this.activityLogService = activityLogService;
    }

    // =========================
    // SUBJECTS
    // =========================

    public SubjectResponse createSubject(
            SubjectRequest request,
            UserEntity professor) {

        if (subjectRepository
                .findBySubjectCode(request.getSubjectCode())
                .isPresent()) {

            throw new RuntimeException(
                    "Subject code already exists"
            );
        }

        if (subjectRepository
                .findBySubjectName(request.getSubjectName())
                .isPresent()) {

            throw new RuntimeException(
                    "Subject name already exists"
            );
        }

        SubjectEntity subject = new SubjectEntity();
        subject.setProfessor(professorRepository.findByUserId(professor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND")));

        subject.setSubjectCode(request.getSubjectCode());
        subject.setSubjectName(request.getSubjectName());
        subject.setDescription(request.getDescription());
        subject.setYearLevel(request.getYearLevel());

        SubjectEntity savedSubject =
                subjectRepository.save(subject);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Created subject: "
                        + savedSubject.getSubjectName()
        );

        return toSubjectResponse(savedSubject);
    }


    public List<SubjectResponse> viewAllSubjects(UserEntity professor) {

        return subjectRepository.findByProfessor_User_Id(professor.getId())
                .stream()
                .map(this::toSubjectResponse)
                .toList();
    }


    public SubjectResponse viewSubjectById(Long id, UserEntity professor) {

        SubjectEntity subject = requireSubject(id, professor);

        return toSubjectResponse(subject);
    }


    public SubjectResponse updateSubject(
            Long id,
            SubjectUpdateRequest request,
            UserEntity professor) {

        SubjectEntity subject = requireSubject(id, professor);

        subject.setSubjectCode(request.getSubjectCode());
        subject.setSubjectName(request.getSubjectName());
        subject.setDescription(request.getDescription());
        subject.setYearLevel(request.getYearLevel());

        SubjectEntity updatedSubject =
                subjectRepository.save(subject);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Updated subject: "
                        + updatedSubject.getSubjectName()
        );

        return toSubjectResponse(updatedSubject);
    }


    public void deleteSubject(
            Long id,
            UserEntity professor) {

        SubjectEntity subject = requireSubject(id, professor);

        String subjectName =
                subject.getSubjectName();

        subjectRepository.delete(subject);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Deleted subject: " + subjectName
        );
    }


    // =========================
    // WEEKS
    // =========================

    public WeekResponse createWeek(
            Long subjectId,
            WeekRequest request,
            UserEntity professor) {

        SubjectEntity subject = requireSubject(subjectId, professor);

        WeekEntity week = new WeekEntity();

        week.setWeekNumber(request.getWeekNumber());
        week.setTitle(request.getTitle());
        week.setSubject(subject);

        WeekEntity savedWeek =
                weekRepository.save(week);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Created Week "
                        + savedWeek.getWeekNumber()
                        + ": "
                        + savedWeek.getTitle()
        );

        return toWeekResponse(savedWeek);
    }


    public List<WeekResponse> viewAllWeeks(
            Long subjectId, UserEntity professor) {

        requireSubject(subjectId, professor);

        return weekRepository
                .findBySubject_Id(subjectId)
                .stream()
                .map(this::toWeekResponse)
                .toList();
    }


    public WeekResponse viewWeekById(Long id, UserEntity professor) {

        WeekEntity week = requireWeek(id, professor);

        return toWeekResponse(week);
    }


    public WeekResponse updateWeek(
            Long id,
            WeekUpdateRequest request,
            UserEntity professor) {

        WeekEntity week = requireWeek(id, professor);

        week.setWeekNumber(request.getWeekNumber());
        week.setTitle(request.getTitle());

        WeekEntity updatedWeek =
                weekRepository.save(week);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Updated Week "
                        + updatedWeek.getWeekNumber()
                        + ": "
                        + updatedWeek.getTitle()
        );

        return toWeekResponse(updatedWeek);
    }


    public void deleteWeek(
            Long id,
            UserEntity professor) {

        WeekEntity week = requireWeek(id, professor);

        Integer weekNumber =
                week.getWeekNumber();

        String weekTitle =
                week.getTitle();

        weekRepository.delete(week);

        activityLogService.createLog(
                professor,
                ActivityType.MODULE,
                "Deleted Week "
                        + weekNumber
                        + ": "
                        + weekTitle
        );
    }


    // =========================
    // RESPONSE MAPPERS
    // =========================

    private SubjectEntity requireSubject(Long id, UserEntity professor) {
        return subjectRepository.findByIdAndProfessor_User_Id(id, professor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private WeekEntity requireWeek(Long id, UserEntity professor) {
        return weekRepository.findByIdAndSubject_Professor_User_Id(id, professor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND"));
    }

    private SubjectResponse toSubjectResponse(
            SubjectEntity subject) {

        SubjectResponse response =
                new SubjectResponse();

        response.setId(subject.getId());
        response.setSubjectCode(
                subject.getSubjectCode()
        );
        response.setSubjectName(
                subject.getSubjectName()
        );
        response.setDescription(
                subject.getDescription()
        );
        response.setYearLevel(
                subject.getYearLevel()
        );

        return response;
    }


    private WeekResponse toWeekResponse(
            WeekEntity week) {

        WeekResponse response =
                new WeekResponse();

        response.setId(week.getId());
        response.setWeekNumber(
                week.getWeekNumber()
        );
        response.setTitle(
                week.getTitle()
        );
        response.setSubjectId(
                week.getSubject().getId()
        );

        return response;
    }
}