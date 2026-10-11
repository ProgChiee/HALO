import { useCallback, useState } from 'react';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { professorErrorMessage } from '../../../utils/professorErrors';
import styles from '../styles/StudentProgress.module.css';

export default function LearningEngagement() {
  const [page, setPage] = useState(0);
  return <EngagementPage key={page} page={page} changePage={setPage} />;
}

function EngagementPage({ page, changePage }) {
  const { api } = useProfessorWorkflow();
  const load = useCallback(config => api.getLearningEngagement(page, 20, config), [api, page]);
  const { data, isLoading, error, reload } = useRemoteData(load);
  return <section aria-labelledby="learning-engagement-title" className={styles.engagement}>
    <h2 id="learning-engagement-title">Learning Engagement</h2>
    <p className={styles.engagementHelp}>Only students eligible for your subjects. Study days include lesson opens, assessments, completions, and Mentor messages. Lesson-open history starts when tracking is deployed.</p>
    {isLoading ? <p role="status">Loading engagement...</p> : error ? <div role="alert"><p>{professorErrorMessage(error, 'Could not load learning engagement. Please try again.')}</p><button onClick={reload}>Retry engagement</button></div> : <>
      <p className={styles.engagementHelp}>Weeks start Monday. Dates use {data?.timezone || 'the server timezone'}. Mentor sessions count once per period when the Student sends a message.</p>
      <div className={styles.tableCard} role="region" aria-label="Learning engagement table, scroll horizontally" tabIndex={0}>
        <table className={styles.progressTable} aria-label="Learning engagement">
          <thead><tr className={styles.tableHeaderRow}><th scope="col">Student</th><th scope="col">Active Study Days<br />This Week</th><th scope="col">Active Study Days<br />This Month</th><th scope="col">AI Mentor Sessions<br />This Week</th><th scope="col">AI Mentor Sessions<br />This Month</th></tr></thead>
          <tbody>{(data?.content ?? []).map(student => <tr key={student.userId} className={styles.tableRow}><th scope="row">{student.name || 'Student'}</th><td>{student.activeDaysWeek}</td><td>{student.activeDaysMonth}</td><td>{student.mentorSessionsWeek}</td><td>{student.mentorSessionsMonth}</td></tr>)}
            {!data?.content?.length && <tr><td colSpan={5} className={styles.emptyState}>No students are currently eligible for your subjects.</td></tr>}
          </tbody>
        </table>
      </div>
      <nav aria-label="Learning engagement pagination"><button disabled={!data || data.first} onClick={() => changePage(page - 1)}>Previous</button><span> Page {page + 1} of {Math.max(1, data?.totalPages ?? 0)} </span><button disabled={!data || data.last} onClick={() => changePage(page + 1)}>Next</button></nav>
    </>}
  </section>;
}
