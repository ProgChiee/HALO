import { useCallback, useState } from 'react';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { professorErrorMessage } from '../../../utils/professorErrors';
import styles from '../styles/StudentProgress.module.css';

// API timestamps are local wall times; do not reinterpret them in the browser's zone.
const dateLabel = value => value ? value.replace('T', ' ').slice(0, 19) : 'Not recorded';

export default function AssessmentAttemptHistory({ studentId }) {
  const { api } = useProfessorWorkflow();
  const [page, setPage] = useState(0);
  const load = useCallback(config => api.getStudentAssessmentHistory(studentId, config), [api, studentId]);
  const { data, isLoading, error, reload } = useRemoteData(load);
  if (isLoading) return <p role="status">Loading assessment history...</p>;
  if (error) return <div role="alert"><p>{professorErrorMessage(error, 'Could not load assessment history. Please try again.')}</p><button className={styles.compactButton} onClick={reload}>Retry assessment history</button></div>;
  if (!data?.length) return <p className={styles.engagementHelp}>No completed assessment attempts in your subject scope.</p>;
  const pages = Math.ceil(data.length / 10);
  return <>
    <p className={styles.engagementHelp}>Completed attempts only. Scores belong to the quiz version taken and are not recalculated when a quiz is replaced. Dates are shown as recorded.</p>
    <div className={styles.tableCard} role="region" aria-label="Assessment attempt history, scroll horizontally" tabIndex={0}>
      <table className={styles.progressTable}>
        <caption>Historical assessment results</caption>
        <thead><tr className={styles.tableHeaderRow}><th scope="col">Assessment</th><th scope="col">Quiz version</th><th scope="col">Score</th><th scope="col">Result</th><th scope="col">Attempt dates</th></tr></thead>
        <tbody>{data.slice(page * 10, (page + 1) * 10).map(attempt => <tr className={styles.tableRow} key={attempt.attemptId}>
          <th scope="row">{attempt.subjectName}<br />Week {attempt.weekNumber}: {attempt.assessmentTitle}<br /><small>Attempt #{attempt.attemptId} · Module #{attempt.moduleId ?? 'Not recorded'}</small></th>
          <td>{attempt.quizVersion == null ? 'Version not recorded' : `Version ${attempt.quizVersion}`}<br /><small>Assessment #{attempt.assessmentId ?? 'Not recorded'}</small></td>
          <td>{attempt.score == null ? 'Score unavailable' : `${attempt.score}%`}</td>
          <td>{attempt.passed === true ? 'Passed' : attempt.passed === false ? 'Not passed' : 'Completed — result unavailable'}</td>
          <td>Started: {dateLabel(attempt.startedAt)}<br />Submitted: {dateLabel(attempt.submittedAt)}</td>
        </tr>)}</tbody>
      </table>
    </div>
    {pages > 1 && <nav aria-label="Assessment history pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Previous attempts</button><span> Page {page + 1} of {pages} </span><button disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next attempts</button></nav>}
  </>;
}
