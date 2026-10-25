package com.ptc.halo.service;


import com.ptc.halo.dtoRequest.*;
import com.ptc.halo.dtoResponse.*;
import com.ptc.halo.entity.*;
import com.ptc.halo.enums.ActivityType;
import com.ptc.halo.enums.Role;
import com.ptc.halo.enums.Status;
import com.ptc.halo.repository.*;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class AdminService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private final UserRepository userRepository;
    private final ProfessorRepository professorRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLogService;
    private final StudentProfileRepository studentProfileRepository;
    private final SubjectRepository subjectRepository;
    private final WeekRepository weekRepository;
    private final ActivityLogRepository activityLogRepository;

    public AdminService(
            UserRepository userRepository,
            ProfessorRepository professorRepository,
            PasswordEncoder passwordEncoder,
            ActivityLogService activityLogService,
            StudentProfileRepository studentProfileRepository,
            SubjectRepository subjectRepository, WeekRepository weekRepository, ActivityLogRepository activityLogRepository
    ){

        this.userRepository = userRepository;
        this.professorRepository = professorRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogService = activityLogService;
        this.studentProfileRepository = studentProfileRepository;
        this.subjectRepository = subjectRepository;
        this.weekRepository = weekRepository;
        this.activityLogRepository = activityLogRepository;
    }



    @org.springframework.transaction.annotation.Transactional
    public ProfessorsResponse createProfessor(ProfessorRequest request, UserEntity admin){

        request.setEmail(request.getEmail()); // Normalize at the service boundary as well as DTO binding.
        if(userRepository.existsNormalizedEmail(request.getEmail(), null)){
            throw new AdminApiException(AdminApiException.Code.EMAIL_ALREADY_EXISTS);
        }
        
        request.setProfessorId(request.getProfessorId());
        if (professorRepository.existsNormalizedProfessorId(request.getProfessorId(), null))
            throw new AdminApiException(AdminApiException.Code.PROFESSOR_ID_CONFLICT);
        UserEntity professor = new UserEntity();

        professor.setName(request.getName());
        professor.setEmail(request.getEmail());

        professor.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        professor.setRole(Role.PROFESSOR);
        professor.setStatus(Status.ACTIVE);


        UserEntity savedProfessor = userRepository.save(professor);


        ProfessorEntity profile = new ProfessorEntity();

        profile.setProfessorId(request.getProfessorId());

        profile.setUser(savedProfessor);

        professorRepository.save(profile);

        activityLogService.createLog(
                admin,
                ActivityType.ACCOUNT,
                "Created Professor account: "
                        + savedProfessor.getEmail()
        );

        ProfessorsResponse response =
                new ProfessorsResponse();


        response.setName(savedProfessor.getName());
        response.setEmail(savedProfessor.getEmail());
        response.setProfessorId(profile.getProfessorId());
        response.setRole(savedProfessor.getRole());


        return response;

    }
    public List<ProfessorResponse> viewAllProfessors(){

        return professorRepository.findAll()
                .stream()
                .map(professor -> {

                    ProfessorResponse response = new ProfessorResponse();

                    response.setId(professor.getUser().getId());
                    response.setName(professor.getUser().getName());
                    response.setEmail(professor.getUser().getEmail());
                    response.setProfessorId(professor.getProfessorId());
                    response.setStatus(professor.getUser().getStatus());

                    return response;

                })
                .collect(Collectors.toList());
    }
    public ProfessorResponse viewProfessorById(Long id){

        ProfessorEntity professor = professorRepository
                .findByUserId(id)
                .orElseThrow(() ->
                        new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND)
                );

        ProfessorResponse response = new ProfessorResponse();

        response.setId(professor.getUser().getId());
        response.setName(professor.getUser().getName());
        response.setEmail(professor.getUser().getEmail());
        response.setProfessorId(professor.getProfessorId());
        response.setStatus(professor.getUser().getStatus());

        return response;
    }
    @org.springframework.transaction.annotation.Transactional
    public ProfessorResponse updateProfessor(
            Long id,
            ProfessorUpdateRequest request,
            UserEntity admin){

        // Use the same durable user-row lock as lifecycle and credential changes.
        // Refresh also protects callers whose persistence context already contains
        // this account from before a concurrent security update committed.
        UserEntity user = entityManager.find(UserEntity.class, id, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (user == null) throw new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND);
        entityManager.refresh(user, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        ProfessorEntity professor = professorRepository
                .findByUserId(id)
                .orElseThrow(() ->
                        new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND)
                );

        request.setProfessorId(request.getProfessorId());
        if (professorRepository.existsNormalizedProfessorId(request.getProfessorId(), professor.getId()))
            throw new AdminApiException(AdminApiException.Code.PROFESSOR_ID_CONFLICT);
        request.setEmail(request.getEmail());
        if (userRepository.existsNormalizedEmail(request.getEmail(), user.getId()))
            throw new AdminApiException(AdminApiException.Code.EMAIL_ALREADY_EXISTS);
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        professor.setProfessorId(request.getProfessorId());

        // Both entities are managed in this transaction; save explicitly rather than
        // depending on cascade merge from a detached ProfessorEntity.
        UserEntity savedUser = userRepository.saveAndFlush(user);
        professorRepository.saveAndFlush(professor);

        activityLogService.createLog(
                admin,
                ActivityType.ACCOUNT,
                "Updated Professor account: "
                        + savedUser.getEmail()
        );

        ProfessorResponse response = new ProfessorResponse();

        response.setId(savedUser.getId());
        response.setName(savedUser.getName());
        response.setEmail(savedUser.getEmail());
        response.setProfessorId(professor.getProfessorId());
        response.setStatus(savedUser.getStatus());

        return response;
    }
    @org.springframework.transaction.annotation.Transactional
    public ProfessorResponse changeProfessorStatus(Long id, Status desiredStatus, UserEntity admin){

        UserEntity professorUser = userRepository.findForAssessmentLifecycle(id)
                .orElseThrow(() ->
                        new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND)
                );


        if(professorUser.getRole() != Role.PROFESSOR){
            throw new AdminApiException(AdminApiException.Code.INVALID_TARGET_ROLE);
        }

        if (professorUser.getStatus() == Status.ARCHIVED) throw new AdminApiException(AdminApiException.Code.ACCOUNT_STATE_CONFLICT);
        if (professorUser.getStatus() != desiredStatus) professorUser.setTokenVersion(professorUser.getTokenVersion() + 1);
        professorUser.setStatus(desiredStatus);


        UserEntity updatedUser = userRepository.save(professorUser);

        activityLogService.createLog(
                admin,
                ActivityType.ACCOUNT,
                "Set Professor status to "
                        + updatedUser.getStatus()
                        + ": "
                        + updatedUser.getEmail()
        );

        ProfessorEntity professor = professorRepository.findByUserId(id)
                .orElseThrow(() ->
                        new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND)
                );

        ProfessorResponse response = new ProfessorResponse();

        response.setId(updatedUser.getId());
        response.setName(updatedUser.getName());
        response.setEmail(updatedUser.getEmail());
        response.setProfessorId(professor.getProfessorId());
        response.setStatus(updatedUser.getStatus());


        return response;
    }
    @org.springframework.transaction.annotation.Transactional
    public Object archiveOrRestore(Long id, Role role, UserEntity admin, boolean restore) {
        if (admin == null || admin.getRole() != Role.ADMIN) throw new org.springframework.security.access.AccessDeniedException("Admin required");
        if (role != Role.PROFESSOR && role != Role.STUDENT) throw new AdminApiException(AdminApiException.Code.INVALID_TARGET_ROLE);
        UserEntity target = userRepository.findForAssessmentLifecycle(id).orElseThrow(() -> new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND));
        if (target.getRole() != role) throw new AdminApiException(AdminApiException.Code.INVALID_TARGET_ROLE);
        Status before = target.getStatus();
        if (restore && before == Status.ARCHIVED) {
            Status previous = target.getPreviousStatusBeforeArchive();
            if (previous != Status.ACTIVE && previous != Status.INACTIVE) throw new AdminApiException(AdminApiException.Code.ACCOUNT_STATE_CONFLICT);
            target.setStatus(previous);
            target.setPreviousStatusBeforeArchive(null);
        } else if (!restore && before != Status.ARCHIVED) {
            if (before != Status.ACTIVE && before != Status.INACTIVE) throw new AdminApiException(AdminApiException.Code.ACCOUNT_STATE_CONFLICT);
            target.setPreviousStatusBeforeArchive(before);
            target.setStatus(Status.ARCHIVED);
        }
        if (before != target.getStatus()) {
            target.setTokenVersion(target.getTokenVersion() + 1);
            userRepository.saveAndFlush(target);
            activityLogService.createLog(admin, ActivityType.ACCOUNT, (restore ? "Restored " : "Archived ") + role + " account ID " + id + ": " + before + " -> " + target.getStatus());
        }
        return role == Role.PROFESSOR ? viewProfessorById(id) : viewStudentById(id);
    }
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public List<StudentListResponse> viewAllStudents(){return studentProfileRepository.findManagementAccounts();}
    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public StudentListResponse viewStudentById(Long id){
        var user=userRepository.findById(id).orElseThrow(()->new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND));
        if(user.getRole()!=Role.STUDENT)throw new AdminApiException(AdminApiException.Code.INVALID_TARGET_ROLE);
        return new StudentListResponse(user,studentProfileRepository.findByUserId(id).orElse(null));
    }
    @org.springframework.transaction.annotation.Transactional
    public StudentListResponse changeStudentStatus(Long id, Status desiredStatus, UserEntity admin){

        UserEntity student = userRepository.findForAssessmentLifecycle(id)
                .orElseThrow(() ->
                        new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND)
                );

        if(student.getRole() != Role.STUDENT){
            throw new AdminApiException(AdminApiException.Code.INVALID_TARGET_ROLE);
        }

        if (student.getStatus() == Status.ARCHIVED) throw new AdminApiException(AdminApiException.Code.ACCOUNT_STATE_CONFLICT);
        if (student.getStatus() != desiredStatus) student.setTokenVersion(student.getTokenVersion() + 1);
        student.setStatus(desiredStatus);


        UserEntity updatedStudent = userRepository.save(student);

        activityLogService.createLog(
                admin,
                ActivityType.ACCOUNT,
                "Set Student status to "
                        + updatedStudent.getStatus()
                        + ": "
                        + updatedStudent.getEmail()
        );

        return new StudentListResponse(updatedStudent,studentProfileRepository.findByUserId(id).orElse(null));
    }
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public StudentListResponse updateStudent(
            Long id,
            StudentUpdateRequest request,
            UserEntity admin){

        if (admin == null || admin.getRole() != Role.ADMIN)
            throw new org.springframework.security.access.AccessDeniedException("Admin access required");
        // Serialize recovery and lifecycle changes for this account. No self-service eligibility changes.
        UserEntity user = userRepository.findForAssessmentLifecycle(id)
                .orElseThrow(() -> new AdminApiException(AdminApiException.Code.RESOURCE_NOT_FOUND));
        if (user.getRole() != Role.STUDENT) throw new AdminApiException(AdminApiException.Code.INVALID_TARGET_ROLE);
        StudentProfileEntity studentProfile = studentProfileRepository.findByUserId(id).orElse(null);
        boolean recovering = studentProfile == null;
        if (recovering) {
            if (admin.getStatus() != Status.ACTIVE)
                throw new org.springframework.security.access.AccessDeniedException("Active Admin access required");
            var details = StudentRegistrationValidator.validateAcademic(request.getStudentId(), request.getSection(), request.getYearLevel());
            request.setStudentId(details.studentId()); request.setSection(details.section());
            if (studentProfileRepository.existsByStudentId(request.getStudentId()))
                throw new AdminApiException(AdminApiException.Code.STUDENT_ID_CONFLICT);
            studentProfile = new StudentProfileEntity();
            studentProfile.setUser(user);
        } else {
            user.setName(request.getName());
        }
        studentProfile.setStudentId(request.getStudentId());
        studentProfile.setSection(request.getSection());
        studentProfile.setYearLevel(request.getYearLevel());

        UserEntity updatedUser = userRepository.save(user);

        StudentProfileEntity updatedProfile = studentProfileRepository.save(studentProfile);

        activityLogService.createLog(
                admin,
                ActivityType.ACCOUNT,
                (recovering ? "Completed Student academic profile: " : "Updated Student account: ")
                        + updatedUser.getEmail()
        );

        StudentListResponse response = new StudentListResponse();

        response.setId(updatedUser.getId());
        response.setName(updatedUser.getName());
        response.setEmail(updatedUser.getEmail());
        response.setStudentId(updatedProfile.getStudentId());
        response.setSection(updatedProfile.getSection());
        response.setYearLevel(updatedProfile.getYearLevel());
        response.setStatus(updatedUser.getStatus());

        return response;
    }

    public SubjectResponse createSubject(
            SubjectRequest request,
            UserEntity admin){

        if(subjectRepository.findBySubjectCode(request.getSubjectCode()).isPresent()){
            throw new RuntimeException("Subject code already exists");
        }

        if(subjectRepository.findBySubjectName(request.getSubjectName()).isPresent()){
            throw new RuntimeException("Subject name already exists");
        }

        SubjectEntity subject = new SubjectEntity();

        subject.setSubjectCode(request.getSubjectCode());
        subject.setSubjectName(request.getSubjectName());
        subject.setDescription(request.getDescription());
        subject.setYearLevel(request.getYearLevel());

        SubjectEntity savedSubject = subjectRepository.save(subject);

        activityLogService.createLog(
                admin,
                ActivityType.MODULE,
                "Created subject: "
                        + savedSubject.getSubjectName()
        );

        SubjectResponse response = new SubjectResponse();

        response.setId(savedSubject.getId());
        response.setSubjectCode(savedSubject.getSubjectCode());
        response.setSubjectName(savedSubject.getSubjectName());
        response.setDescription(savedSubject.getDescription());
        response.setYearLevel(savedSubject.getYearLevel());

        return response;
    }
    public List<SubjectResponse> viewAllSubjects(){

        return subjectRepository.findAll()
                .stream()
                .map(subject -> {

                    SubjectResponse response = new SubjectResponse();


                    response.setId(subject.getId());
                    response.setSubjectCode(subject.getSubjectCode());
                    response.setSubjectName(subject.getSubjectName());
                    response.setDescription(subject.getDescription());
                    response.setYearLevel(subject.getYearLevel());

                    return response;

                })
                .collect(Collectors.toList());
    }
    public SubjectResponse viewSubjectById(Long id){

        SubjectEntity subject =
                subjectRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Subject not found")
                        );


        SubjectResponse response =
                new SubjectResponse();


        response.setId(subject.getId());
        response.setSubjectCode(subject.getSubjectCode());
        response.setSubjectName(subject.getSubjectName());
        response.setDescription(subject.getDescription());
        response.setYearLevel(subject.getYearLevel());


        return response;
    }
    public SubjectResponse updateSubject(
            Long id,
            SubjectUpdateRequest request,
            UserEntity admin){

        SubjectEntity subject =
                subjectRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Subject not found")
                        );


        subject.setSubjectCode(
                request.getSubjectCode()
        );


        subject.setSubjectName(
                request.getSubjectName()
        );


        subject.setDescription(
                request.getDescription()
        );


        subject.setYearLevel(
                request.getYearLevel()
        );


        SubjectEntity updatedSubject =
                subjectRepository.save(subject);


        activityLogService.createLog(
                admin,
                ActivityType.MODULE,
                "Updated subject: "
                        + updatedSubject.getSubjectName()
        );

        SubjectResponse response =
                new SubjectResponse();

        response.setId(updatedSubject.getId());
        response.setSubjectCode(updatedSubject.getSubjectCode());
        response.setSubjectName(updatedSubject.getSubjectName());
        response.setDescription(updatedSubject.getDescription());
        response.setYearLevel(updatedSubject.getYearLevel());


        return response;
    }
    public void deleteSubject(Long id, UserEntity admin){

        SubjectEntity subject =
                subjectRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Subject not found")
                        );

        String subjectName =
                subject.getSubjectName();

        subjectRepository.delete(subject);

        activityLogService.createLog(
                admin,
                ActivityType.MODULE,
                "Deleted subject: "
                        + subjectName
        );
    }
    public WeekResponse createWeek(
            Long subjectId,
            WeekRequest request,
            UserEntity admin){

        SubjectEntity subject =
                subjectRepository.findById(subjectId)
                        .orElseThrow(() ->
                                new RuntimeException("Subject not found")
                        );


        WeekEntity week = new WeekEntity();


        week.setWeekNumber(request.getWeekNumber());
        week.setTitle(request.getTitle());
        week.setSubject(subject);

        WeekEntity savedWeek =
                weekRepository.save(week);

        activityLogService.createLog(
                admin,
                ActivityType.MODULE,
                "Created Week "
                        + savedWeek.getWeekNumber()
                        + ": "
                        + savedWeek.getTitle()
        );

        WeekResponse response =
                new WeekResponse();

        response.setId(savedWeek.getId());
        response.setWeekNumber(savedWeek.getWeekNumber());
        response.setTitle(savedWeek.getTitle());
        response.setSubjectId(subject.getId());

        return response;
    }
    public List<WeekResponse> viewAllWeeks(Long subjectId){

        SubjectEntity subject =
                subjectRepository.findById(subjectId)
                        .orElseThrow(() ->
                                new RuntimeException("Subject not found")
                        );


        return weekRepository.findBySubject_Id(subjectId)
                .stream()
                .map(week -> {

                    WeekResponse response = new WeekResponse();

                    response.setId(week.getId());
                    response.setWeekNumber(week.getWeekNumber());
                    response.setTitle(week.getTitle());
                    response.setSubjectId(subject.getId());

                    return response;

                })
                .toList();
    }
    public WeekResponse viewWeekById(Long id){

        WeekEntity week =
                weekRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Week not found")
                        );


        WeekResponse response = new WeekResponse();

        response.setId(week.getId());
        response.setWeekNumber(week.getWeekNumber());
        response.setTitle(week.getTitle());
        response.setSubjectId(week.getSubject().getId());

        return response;
    }
    public WeekResponse updateWeek(
            Long id,
            WeekUpdateRequest request,
            UserEntity admin){

        WeekEntity week =
                weekRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Week not found"));

        week.setWeekNumber(request.getWeekNumber());
        week.setTitle(request.getTitle());

        WeekEntity updatedWeek = weekRepository.save(week);

        activityLogService.createLog(
                admin,
                ActivityType.MODULE,
                "Updated Week "
                        + updatedWeek.getWeekNumber()
                        + ": "
                        + updatedWeek.getTitle()
        );

        WeekResponse response = new WeekResponse();

        response.setId(updatedWeek.getId());
        response.setWeekNumber(updatedWeek.getWeekNumber());
        response.setTitle(updatedWeek.getTitle());
        response.setSubjectId(updatedWeek.getSubject().getId());

        return response;
    }
    public void deleteWeek(Long id, UserEntity admin){

        WeekEntity week =
                weekRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException("Week not found")
                        );

        var weekNumber =
                week.getWeekNumber();

        String weekTitle =
                week.getTitle();

        weekRepository.delete(week);

        activityLogService.createLog(
                admin,
                ActivityType.MODULE,
                "Deleted Week "
                        + weekNumber
                        + ": "
                        + weekTitle
        );
    }
    public List<AdminRecentActivityResponse>
    getRecentActivity() {

        List<ActivityLogEntity> logs =
                activityLogRepository
                        .findTop10ByOrderByCreatedAtDesc();

        List<AdminRecentActivityResponse> responses =
                new ArrayList<>();


        var display = ActivityDisplay.prepare(logs, userRepository);
        for (ActivityLogEntity log : logs) {

            AdminRecentActivityResponse response =
                    new AdminRecentActivityResponse();

            response.setId(
                    log.getId()
            );

            response.setUserName(
                    log.getUser() == null && log.getAuthEvent() != null ? "Unknown account" : ActivityDisplay.name(log.getUser(), "Admin")
            );

            response.setEmail(
                    log.getUser() == null ? null : log.getUser().getEmail()
            );

            response.setRole(
                    log.getUser() == null ? null : log.getUser().getRole()
            );

            response.setActivityType(
                    log.getActivityType()
            );

            response.setDisplayAction(display.apply(log));
            response.setAction(
                    log.getAction()
            );

            response.setCreatedAt(
                    log.getCreatedAt()
            );

            responses.add(response);
        }

        return responses;
    }

}
