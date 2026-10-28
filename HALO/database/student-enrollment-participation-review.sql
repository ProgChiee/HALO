-- READ ONLY. Candidates are participation evidence, NOT enrollment records.
-- No historical timestamp is converted or used as a purported enrollment date.
SELECT u.id AS student_id, w.subject_id, GROUP_CONCAT(DISTINCT evidence.source ORDER BY evidence.source) AS evidence_sources,
       u.status, p.year_level AS student_year, s.year_level AS subject_year,
       (p.year_level=s.year_level AND p.student_id IS NOT NULL AND TRIM(p.student_id)<>''
          AND p.section IS NOT NULL AND TRIM(p.section)<>'') AS currently_profile_eligible
FROM (
 SELECT student_id,module_id,'LESSON_STUDY_DAY' AS source FROM lesson_study_days
 UNION ALL
 SELECT student_id,module_id,'COMPLETED_PROGRESS' FROM student_module_progress WHERE completed=TRUE
 UNION ALL
 SELECT t.student_id,a.module_id,'ASSESSMENT_ATTEMPT' FROM assessment_attempts t
 JOIN assessments a ON a.id=t.assessment_id WHERE t.started_at IS NOT NULL OR t.submitted_at IS NOT NULL
 UNION ALL
 SELECT s.student_id,s.ai_learning_module_id,'STUDENT_MENTOR_MESSAGE' FROM mentor_sessions s
 JOIN mentor_messages m ON m.session_id=s.id WHERE m.sender='STUDENT' AND LENGTH(TRIM(m.message))>0
) evidence
JOIN user_entity u ON u.id=evidence.student_id AND u.role='STUDENT'
JOIN ai_learning_modules m ON m.id=evidence.module_id
JOIN weeks w ON w.id=m.week_id
JOIN subjects s ON s.id=w.subject_id
LEFT JOIN student_profile_entity p ON p.user_id=u.id
GROUP BY u.id,w.subject_id,u.status,p.year_level,s.year_level,p.student_id,p.section
ORDER BY u.id,w.subject_id;

