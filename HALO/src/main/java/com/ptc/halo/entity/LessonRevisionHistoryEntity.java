package com.ptc.halo.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;

/** Insert-only published content. Draft edits never reference this entity for mutation. */
@Entity
@Immutable
@Table(name="lesson_revision_history", uniqueConstraints=@UniqueConstraint(
        name="uq_lesson_history_version",columnNames={"module_id","revision_number"}))
public class LessonRevisionHistoryEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long moduleId;
    @Column(nullable=false,updatable=false) private int revisionNumber;
    @Column(nullable=false,updatable=false,length=24) private String snapshotKind;
    @Column(updatable=false) private Long revisionVersion;
    @Column(updatable=false) private Long baseVersion;
    @Column(updatable=false) private Long publishedModuleVersion;
    @Column(nullable=false,updatable=false,columnDefinition="LONGTEXT") private String payload;
    @Column(updatable=false) private Long revisedBy;
    @Column(updatable=false) private LocalDateTime revisedAt;
    @Column(updatable=false) private Long publishedBy;
    @Column(updatable=false) private LocalDateTime publishedAt;
    @Column(nullable=false,updatable=false) private LocalDateTime recordedAt;

    protected LessonRevisionHistoryEntity() {}
    public LessonRevisionHistoryEntity(Long moduleId,int revisionNumber,String snapshotKind,
            Long revisionVersion,Long baseVersion,Long publishedModuleVersion,String payload,
            Long revisedBy,LocalDateTime revisedAt,Long publishedBy,LocalDateTime publishedAt) {
        this.moduleId=moduleId;this.revisionNumber=revisionNumber;this.snapshotKind=snapshotKind;
        this.revisionVersion=revisionVersion;this.baseVersion=baseVersion;this.publishedModuleVersion=publishedModuleVersion;
        this.payload=payload;this.revisedBy=revisedBy;this.revisedAt=revisedAt;
        this.publishedBy=publishedBy;this.publishedAt=publishedAt;this.recordedAt=LocalDateTime.now();
    }
    @PreRemove private void preventDelete(){throw new IllegalStateException("Published lesson history is immutable");}
    public Long getId(){return id;}
    public Long getModuleId(){return moduleId;}
    public int getRevisionNumber(){return revisionNumber;}
    public String getSnapshotKind(){return snapshotKind;}
    public Long getRevisionVersion(){return revisionVersion;}
    public Long getBaseVersion(){return baseVersion;}
    public Long getPublishedModuleVersion(){return publishedModuleVersion;}
    public String getPayload(){return payload;}
    public Long getRevisedBy(){return revisedBy;}
    public LocalDateTime getRevisedAt(){return revisedAt;}
    public Long getPublishedBy(){return publishedBy;}
    public LocalDateTime getPublishedAt(){return publishedAt;}
    public LocalDateTime getRecordedAt(){return recordedAt;}
}
