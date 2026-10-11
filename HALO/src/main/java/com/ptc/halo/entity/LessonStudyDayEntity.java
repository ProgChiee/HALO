package com.ptc.halo.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity
@Table(name="lesson_study_days",uniqueConstraints=@UniqueConstraint(name="uk_lesson_study_day",columnNames={"student_id","module_id","study_date"}),
 indexes=@Index(name="idx_study_module_date",columnList="module_id,study_date,student_id"))
public class LessonStudyDayEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="student_id",nullable=false) private Long studentId;
 @Column(name="module_id",nullable=false) private Long moduleId;
 @Column(name="study_date",nullable=false) private LocalDate studyDate;
 @Column(name="last_opened_at") private java.time.LocalDateTime lastOpenedAt;
 @Column(name="review_count",nullable=false) private long reviewCount;
 public LocalDate getStudyDate(){return studyDate;}
 public java.time.LocalDateTime getLastOpenedAt(){return lastOpenedAt;}
 public void setLastOpenedAt(java.time.LocalDateTime value){lastOpenedAt=value;}
 public long getReviewCount(){return reviewCount;}
 public void addReview(){reviewCount++;}
 public LessonStudyDayEntity() {}
 public LessonStudyDayEntity(Long studentId,Long moduleId,LocalDate date){this.studentId=studentId;this.moduleId=moduleId;this.studyDate=date;}
}
