package com.ptc.halo;

import com.ptc.halo.entity.*;
import com.ptc.halo.enums.*;
import com.ptc.halo.repository.*;
import com.ptc.halo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentIncompleteProfileTest {
    final StudentProfileRepository profiles = mock(StudentProfileRepository.class);
    final SubjectRepository subjects = mock(SubjectRepository.class);
    final WeekRepository weeks = mock(WeekRepository.class);
    final ProfileService profileService = new ProfileService(profiles, mock(ProfessorRepository.class));
    final StudentSubjectService subjectService = new StudentSubjectService(subjects, profiles, weeks,
            mock(AiLearningModuleRepository.class), mock(StudentModuleProgressRepository.class));
    UserEntity student() {
        var user = new UserEntity(); user.setId(26L); user.setName("Student"); user.setEmail("student@example.test");
        user.setRole(Role.STUDENT); user.setStatus(Status.ACTIVE); return user;
    }
    @Test void missingProfileReturnsOwnIdentityWithoutInventingAcademicFields() {
        var response = profileService.getStudentProfile(student());
        assertEquals(26L, response.getUserId()); assertEquals("Student", response.getName());
        assertEquals("student@example.test", response.getEmail()); assertTrue(response.isProfileIncomplete());
        assertNull(response.getStudentId()); assertNull(response.getYearLevel()); assertNull(response.getSection());
        verify(profiles).findByUserId(26L); verifyNoMoreInteractions(profiles);
    }
    @Test void missingProfileAndMissingYearReturnTypedConflictWithoutQueryingSubjects() {
        var error = assertThrows(ResponseStatusException.class, () -> subjectService.getStudentSubjects(student()));
        assertEquals(409, error.getStatusCode().value()); assertEquals("PROFILE_INCOMPLETE", error.getReason());
        when(profiles.findByUserId(26L)).thenReturn(Optional.of(new StudentProfileEntity()));
        assertEquals("PROFILE_INCOMPLETE", assertThrows(ResponseStatusException.class,
                () -> subjectService.getStudentSubjects(student())).getReason());
        verifyNoInteractions(subjects, weeks);
    }
    @Test void completedProfileKeepsExistingYearBasedEligibilityAndLegacyId() {
        var profile = new StudentProfileEntity(); profile.setStudentId("LEGACY-26");
        profile.setSection("BSHM 1A"); profile.setYearLevel(YearLevel.FIRST_YEAR);
        when(profiles.findByUserId(26L)).thenReturn(Optional.of(profile));
        assertFalse(profileService.getStudentProfile(student()).isProfileIncomplete());
        assertEquals("LEGACY-26", profileService.getStudentProfile(student()).getStudentId());
        assertTrue(subjectService.getStudentSubjects(student()).isEmpty());
        verify(subjects).findByYearLevel(YearLevel.FIRST_YEAR);
    }
    @Test void missingProfileCannotBypassDirectSubjectAccess() {
        var access = new StudentLessonAccessService(mock(UserRepository.class), profiles);
        var subject = new SubjectEntity(); subject.setYearLevel(YearLevel.FIRST_YEAR);
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> access.validateSubject(student(), subject)).getStatusCode().value());
    }
}
