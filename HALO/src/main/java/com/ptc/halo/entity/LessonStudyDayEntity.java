package com.ptc.halo.entity;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity
@Table(name="lesson_study_days",uniqueConstraints=@UniqueConstraint(name="uk_lesson_study_day",columnNames={"student_id","module_id","study_date"}))
public class LessonStudyDayEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="student_id",nullable=false) private Long studentId;
 @Column(name="module_id",nullable=false) private Long moduleId;
 @Column(name="study_date",nullable=false) private LocalDate studyDate;
 public LessonStudyDayEntity() {}
 public LessonStudyDayEntity(Long studentId,Long moduleId,LocalDate date){this.studentId=studentId;this.moduleId=moduleId;this.studyDate=date;}
}
