package com.ptc.halo.repository;

import com.ptc.halo.entity.MentorMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MentorMessageRepository
        extends JpaRepository<MentorMessageEntity, Long> {
    java.util.Optional<MentorMessageEntity> findBySessionIdAndRequestIdAndSender(Long sessionId, String requestId, com.ptc.halo.enums.MessageSender sender);

    @org.springframework.data.jpa.repository.Query("select m from MentorMessageEntity m where m.session.id = :sessionId and (:beforeId is null or m.id < :beforeId) order by m.id desc")
    List<MentorMessageEntity> findWindow(
            @org.springframework.data.repository.query.Param("sessionId") Long sessionId,
            @org.springframework.data.repository.query.Param("beforeId") Long beforeId,
            org.springframework.data.domain.Pageable page);

    List<MentorMessageEntity>
    findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);
}
