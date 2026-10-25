package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ContinueLearningAccessTest {
    @Test void checksSubjectAccessBeforeReadingActivity() {
        var weeks=mock(WeekRepository.class); var subjects=mock(SubjectRepository.class);
        var access=mock(StudentLessonAccessService.class); var student=new UserEntity();student.setId(30L);
        var subject=new SubjectEntity();when(subjects.findById(15L)).thenReturn(Optional.of(subject));
        doThrow(new org.springframework.security.access.AccessDeniedException("Denied")).when(access).validateSubject(student,subject);
        var service=new StudentLearningProgressionService(weeks,subjects,mock(AiLearningModuleRepository.class),mock(StudentModuleProgressRepository.class),access);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.getWeekAccess(15L,student));
        verifyNoInteractions(weeks);
    }
    @Test void exposesHistoryOnlyForUnlockedPublishedRows() {
        var weeks=mock(WeekRepository.class);var subjects=mock(SubjectRepository.class);
        var student=new UserEntity();student.setId(30L);
        when(subjects.findById(15L)).thenReturn(Optional.of(new SubjectEntity()));
        var first=mock(WeekRepository.StudentWeekRow.class);when(first.getModuleId()).thenReturn(42L);when(first.getCompletedCount()).thenReturn(0L);
        var second=mock(WeekRepository.StudentWeekRow.class);when(second.getModuleId()).thenReturn(43L);
        when(weeks.findStudentWeekRows(List.of(15L),30L)).thenReturn(List.of(first,second));
        var activity=mock(WeekRepository.ModuleActivity.class);when(activity.getModuleId()).thenReturn(42L);
        var time=java.time.LocalDateTime.of(2026,10,26,1,0);when(activity.getLastActivityAt()).thenReturn(time);
        when(weeks.findModuleActivity(15L,30L)).thenReturn(List.of(activity));
        var service=new StudentLearningProgressionService(weeks,subjects,mock(AiLearningModuleRepository.class),mock(StudentModuleProgressRepository.class),mock(StudentLessonAccessService.class));
        var result=service.getWeekAccess(15L,student);
        assertEquals(time,result.get(0).getLastActivityAt());assertTrue(result.get(0).getUnlocked());
        assertFalse(result.get(1).getUnlocked());assertNull(result.get(1).getLastActivityAt());
    }
}
