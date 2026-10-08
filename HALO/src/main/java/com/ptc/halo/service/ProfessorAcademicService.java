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
import org.springframework.transaction.annotation.Transactional;
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

    @Transactional
    public SubjectResponse createSubject(SubjectRequest request, UserEntity professor) {
        return createSubject(request, professor, null);
    }
    @Transactional
    public SubjectResponse createSubject(SubjectRequest request, UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        if (subjectRepository
                .findBySubjectCode(request.getSubjectCode())
                .isPresent()) {

            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUBJECT_CODE_ALREADY_EXISTS");
        }

        if (subjectRepository
                .findBySubjectName(request.getSubjectName())
                .isPresent()) {

            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUBJECT_NAME_ALREADY_EXISTS");
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

        log(
                professor, acting,
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


    @Transactional
    public SubjectResponse updateSubject(Long id, SubjectUpdateRequest request, UserEntity professor) {
        return updateSubject(id, request, professor, null);
    }
    @Transactional
    public SubjectResponse updateSubject(Long id, SubjectUpdateRequest request, UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        SubjectEntity subject = requireSubject(id, professor);

        subject.setSubjectCode(request.getSubjectCode());
        subject.setSubjectName(request.getSubjectName());
        subject.setDescription(request.getDescription());
        subject.setYearLevel(request.getYearLevel());

        SubjectEntity updatedSubject =
                subjectRepository.save(subject);

        log(
                professor, acting,
                ActivityType.MODULE,
                "Updated subject: "
                        + updatedSubject.getSubjectName()
        );

        return toSubjectResponse(updatedSubject);
    }


    @Transactional
    public void deleteSubject(Long id, UserEntity professor) {
        deleteSubject(id, professor, null);
    }
    @Transactional
    public void deleteSubject(Long id, UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        SubjectEntity subject = requireSubject(id, professor);

        String subjectName =
                subject.getSubjectName();

        if (weekRepository.existsBySubject_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUBJECT_HAS_CONTENT");
        }
        try {
            subjectRepository.delete(subject);
            subjectRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            // A concurrent dependent insert may race the existence check.
            // Keep the FK and roll back the whole transaction.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUBJECT_HAS_CONTENT");
        }

        log(
                professor, acting,
                ActivityType.MODULE,
                "Deleted subject: " + subjectName
        );
    }


    // =========================
    // WEEKS
    // =========================

    @Transactional
    public WeekResponse createWeek(Long subjectId, WeekRequest request, UserEntity professor) {
        return createWeek(subjectId, request, professor, null);
    }
    @Transactional
    public WeekResponse createWeek(Long subjectId, WeekRequest request, UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        SubjectEntity subject = requireSubject(subjectId, professor);

        if (weekRepository.existsBySubject_IdAndWeekNumber(subjectId, request.getWeekNumber())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WEEK_NUMBER_ALREADY_EXISTS");
        }
        WeekEntity week = new WeekEntity();

        week.setWeekNumber(request.getWeekNumber());
        week.setTitle(request.getTitle());
        week.setSubject(subject);

        WeekEntity savedWeek =
                saveWeek(week);

        log(
                professor, acting,
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


    @Transactional
    public WeekResponse updateWeek(Long id, WeekUpdateRequest request, UserEntity professor) {
        return updateWeek(id, request, professor, null);
    }
    @Transactional
    public WeekResponse updateWeek(Long id, WeekUpdateRequest request, UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        WeekEntity week = requireWeek(id, professor);

        if (weekRepository.existsBySubject_IdAndWeekNumberAndIdNot(week.getSubject().getId(), request.getWeekNumber(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "WEEK_NUMBER_ALREADY_EXISTS");
        }
        week.setWeekNumber(request.getWeekNumber());
        week.setTitle(request.getTitle());

        WeekEntity updatedWeek =
                saveWeek(week);

        log(
                professor, acting,
                ActivityType.MODULE,
                "Updated Week "
                        + updatedWeek.getWeekNumber()
                        + ": "
                        + updatedWeek.getTitle()
        );

        return toWeekResponse(updatedWeek);
    }


    @Transactional
    public void deleteWeek(Long id, UserEntity professor) {
        deleteWeek(id, professor, null);
    }
    @Transactional
    public void deleteWeek(Long id, UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting) {

        WeekEntity week = requireWeek(id, professor);

        Integer weekNumber =
                week.getWeekNumber();

        String weekTitle =
                week.getTitle();

        weekRepository.delete(week);

        log(
                professor, acting,
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

    private WeekEntity saveWeek(WeekEntity week) {
        try {
            return weekRepository.saveAndFlush(week);
        } catch (org.springframework.dao.DataIntegrityViolationException error) {
            // Translate only our named uniqueness constraint, never unrelated SQL failures.
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && violation.getConstraintName().toLowerCase(java.util.Locale.ROOT).contains("uk_week_subject_number")) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "WEEK_NUMBER_ALREADY_EXISTS");
                }
            }
            throw error;
        }
    }

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
    private void log(UserEntity professor, com.ptc.halo.entity.AdminActingSessionEntity acting, ActivityType type, String action) {
        if (acting == null) activityLogService.createLog(professor, type, action);
        else activityLogService.createProfessorLog(professor, acting, type, action);
    }
}