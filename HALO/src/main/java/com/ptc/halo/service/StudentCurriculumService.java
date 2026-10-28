package com.ptc.halo.service;
import com.ptc.halo.dtoResponse.StudentCurriculumResponse;
import com.ptc.halo.entity.UserEntity;
import com.ptc.halo.repository.WeekRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@Transactional(readOnly=true)
public class StudentCurriculumService {
    private final StudentEnrollmentService enrollment;
    private final WeekRepository weeks;
    public StudentCurriculumService(StudentEnrollmentService enrollment, WeekRepository weeks) {
        this.enrollment = enrollment; this.weeks = weeks;
    }
    public StudentCurriculumResponse preview(UserEntity student, Long subjectId) {
        var subject = enrollment.list(student, false).stream()
                .filter(s -> s.getSubjectId().equals(subjectId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SUBJECT_UNAVAILABLE"));
        var published = weeks.findPublishedCurriculum(subjectId).stream()
                .map(w -> new StudentCurriculumResponse.PublishedWeek(w.getWeekNumber(), w.getTitle())).toList();
        return new StudentCurriculumResponse(subject.getSubjectId(), subject.getSubjectCode(),
                subject.getSubjectName(), subject.getYearLevel(), published.size(), published);
    }
}
