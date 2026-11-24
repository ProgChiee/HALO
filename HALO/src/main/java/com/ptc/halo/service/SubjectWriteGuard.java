package com.ptc.halo.service;

import com.ptc.halo.entity.SubjectEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

final class SubjectWriteGuard {
    private SubjectWriteGuard() {}

    // Refresh under the lock: an earlier ownership lookup may have cached the subject.
    static void requireWritable(EntityManager em, SubjectEntity subject) {
        em.refresh(subject, LockModeType.PESSIMISTIC_WRITE);
        ProfessorAcademicService.requireActive(subject);
    }
}
