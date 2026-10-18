package com.ptc.halo.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="lesson_revisions")
public class LessonRevisionEntity {
 @Id public Long moduleId;
 @Version public Long version;
 @Column(nullable=false) public Long baseVersion;
 @Column(nullable=false) public int revisionNumber;
 @Column(nullable=false,length=16) public String state;
 @Column(nullable=false,columnDefinition="LONGTEXT") public String payload;
 @Column(nullable=false) public Long revisedBy;
 @Column(nullable=false) public LocalDateTime revisedAt;
 public Long publishedBy;
 public LocalDateTime publishedAt;
}
