package com.ptc.halo.repository;

import com.ptc.halo.entity.StudentProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfileEntity, Long> {
    @org.springframework.data.jpa.repository.Query("select new com.ptc.halo.dtoResponse.StudentListResponse(u,p) from UserEntity u left join StudentProfileEntity p on p.user.id=u.id where u.role=com.ptc.halo.enums.Role.STUDENT order by u.id")
    java.util.List<com.ptc.halo.dtoResponse.StudentListResponse> findManagementAccounts();
    boolean existsByStudentId(String studentId);
    Optional<StudentProfileEntity>
    findByUserId(Long userId);
}
