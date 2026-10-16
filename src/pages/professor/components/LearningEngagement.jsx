import { useCallback, useState } from 'react';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { professorErrorMessage } from '../../../utils/professorErrors';
import styles from '../styles/StudentProgress.module.css';

export default function LearningEngagement() {
  const [page, setPage] = useState(0);
  return <EngagementPage key={page} page={page} changePage={setPage} />;
}

function ModuleActivity({ student, onClose }) {
  const [page, setPage] = useState(0);
  return <section className={styles.moduleActivity} aria-label={`Module activity for ${student.name || 'Student'}`}>
    <div className={styles.moduleHeader}><h3>{student.name || 'Student'} — Module activity</h3>
    <button className={styles.compactButton} onClick={onClose}>Close module activity</button></div>
    <p className={styles.engagementHelp}>Lesson Review: counted when a Student returns to a lesson after 30 minutes.<br />Assessment Attempt: counted when a real assessment is started.</p>
    <ModuleActivityPage key={page} studentId={student.userId} page={page} setPage={setPage} />
  </section>;
}

function ModuleActivityPage({ studentId, page, setPage }) {
  const { api } = useProfessorWorkflow();
  const load = useCallback(config => api.getModuleEngagement(studentId, page, 20, config), [api, studentId, page]);
  const { data, isLoading, error, reload } = useRemoteData(load);
  if (isLoading) return <p role="status">Loading module activity...</p>;
  if (error) return <div role="alert"><p>{professorErrorMessage(error)}</p><button onClick={reload}>Retry module activity</button></div>;
  return <>
    <div className={styles.tableCard} role="region" aria-label="Module activity table, scroll horizontally" tabIndex={0}>
      <table className={styles.progressTable}><caption>Lesson Reviews and Assessment Attempts</caption><thead><tr className={styles.tableHeaderRow}><th scope="col">Subject / Module</th><th scope="col">Review Count</th><th scope="col">Reviews This Month</th><th scope="col">Assessment Attempts</th></tr></thead>
        <tbody>{(data?.content ?? []).map(module => <tr key={module.moduleId} className={styles.tableRow}><th scope="row">{module.subjectName}<br />Week {module.weekNumber}: {module.title}</th><td>{module.reviewCount}</td><td>{module.reviewsMonth}</td><td>{module.assessmentAttempts}</td></tr>)}
          {!data?.content?.length && <tr><td colSpan={4}>No modules in this Student’s eligible subjects.</td></tr>}
        </tbody>
      </table>
    </div>
    <nav aria-label="Module activity pagination"><button disabled={!data || data.first} onClick={() => setPage(page - 1)}>Previous modules</button><span> Page {page + 1} of {Math.max(1, data?.totalPages ?? 0)} </span><button disabled={!data || data.last} onClick={() => setPage(page + 1)}>Next modules</button></nav>
  </>;
}

function EngagementPage({ page, changePage }) {
  const { api } = useProfessorWorkflow();
  const [selected, setSelected] = useState(null);
  const load = useCallback(config => api.getLearningEngagement(page, 20, config), [api, page]);
  const { data, isLoading, error, reload } = useRemoteData(load);
  return <section aria-labelledby="learning-engagement-title" className={styles.engagement}>
    <h2 id="learning-engagement-title">Learning Engagement</h2>
    <p className={styles.engagementHelp}>Only students eligible for your subjects. Study days include lesson opens, assessments, completions, and Mentor messages. Lesson-open history starts when tracking is deployed.</p>
    {isLoading ? <p role="status">Loading engagement...</p> : error ? <div role="alert"><p>{professorErrorMessage(error, 'Could not load learning engagement. Please try again.')}</p><button onClick={reload}>Retry engagement</button></div> : <>

      <div className={styles.tableCard} role="region" aria-label="Learning engagement table, scroll horizontally" tabIndex={0}>
        <table className={styles.progressTable} aria-label="Learning engagement">
          <thead><tr className={styles.tableHeaderRow}><th scope="col">Student</th><th scope="col">Active Study Days<br />This Week</th><th scope="col">Active Study Days<br />This Month</th><th scope="col">AI Mentor Sessions<br />This Week</th><th scope="col">AI Mentor Sessions<br />This Month</th></tr></thead>
          <tbody>{(data?.content ?? []).map(student => <tr key={student.userId} className={styles.tableRow}><th scope="row">{student.name || 'Student'}<br /><button className={styles.compactButton} onClick={() => setSelected(student)}>View module activity</button></th><td>{student.activeDaysWeek}<br /><small>{student.weeklyConsistency}</small></td><td>{student.activeDaysMonth}<br /><small>{student.monthlyConsistency}</small></td><td>{student.mentorSessionsWeek}</td><td>{student.mentorSessionsMonth}</td></tr>)}
            {!data?.content?.length && <tr><td colSpan={5} className={styles.emptyState}>No students are currently eligible for your subjects.</td></tr>}
          </tbody>
        </table>
      </div>
      <details className={styles.metricNotes}><summary>About dates and consistency labels</summary>
      <p className={styles.engagementHelp}>Weeks start Monday. Dates use {data?.timezone || 'the server timezone'}. Mentor sessions count once per period when the Student sends a message.</p>
      <p className={styles.engagementHelp}>Learning Consistency: weekly 3+ active days = Consistent, 2 = Moderate, 0-1 = Low Activity. Monthly 8+ = Consistent, 4-7 = Moderate, 0-3 = Low Activity. These labels describe activity so far, not a score.</p>
      </details>
      <nav aria-label="Learning engagement pagination"><button disabled={!data || data.first} onClick={() => changePage(page - 1)}>Previous</button><span> Page {page + 1} of {Math.max(1, data?.totalPages ?? 0)} </span><button disabled={!data || data.last} onClick={() => changePage(page + 1)}>Next</button></nav>
      {selected && <ModuleActivity key={selected.userId} student={selected} onClose={() => setSelected(null)} />}
    </>}
  </section>;
}
