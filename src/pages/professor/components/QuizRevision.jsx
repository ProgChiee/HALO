import { useEffect, useRef, useState } from 'react';
import Dialog from '../../../components/shared/Dialog';
import Button from '../../../components/shared/Button';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { professorErrorMessage } from '../../../utils/professorErrors';
import styles from '../styles/LessonEditor.module.css';

export default function QuizRevision({ moduleId }) {
 const { api, preview } = useProfessorWorkflow();
 const [state, setState] = useState(null), [review, setReview] = useState(null), [confirm, setConfirm] = useState(null), [busy, setBusy] = useState(false), [error, setError] = useState('');
 const generation = useRef(0), locked = useRef(false);
 useEffect(() => {
  const controller = new AbortController(); const token = ++generation.current;
  if (!preview) api.getQuizRevision(moduleId, { signal: controller.signal }).then(value => { if (generation.current === token && !controller.signal.aborted) setState(value); }).catch(e => { if (!controller.signal.aborted) setError(professorErrorMessage(e)); });
  return () => { generation.current = token + 1; controller.abort(); };
 }, [api, moduleId, preview]);
 if (preview) return null;
 async function run(action) {
  if (locked.current) return;
  const token = generation.current; locked.current = true; setBusy(true); setError('');
  try {
   const value = action === 'generate' ? await api.generateQuizRevision(moduleId) : await api.approveQuizRevision(moduleId, state.draft.id);
   if (generation.current === token) { setState(value); setReview(action === 'generate' ? 'draft' : null); setConfirm(null); }
  } catch (e) { if (generation.current === token) setError(professorErrorMessage(e)); }
  finally { locked.current = false; if (generation.current === token) setBusy(false); }
 }
 const quiz = review && state?.[review];
 return <section className={styles.panelCard} aria-label="Assessment review">
  <h2 className={styles.panelTitle}>{state?.needsReview ? 'Assessment review needed' : 'Assessment'}</h2>
  <p className={styles.panelSubtitle}>{state?.needsReview ? 'Lesson content has changed. Review or regenerate the quiz to ensure it matches the latest published lesson.' : 'New quiz versions remain private until you review and approve them. Previous attempts and scores are preserved.'}</p>
  {error && <p role="alert">{error}</p>}
  {!state && !error && <p role="status">Loading assessment...</p>}
  {state && <>
   {state.current && <p>Current quiz: Version {state.current.version}</p>}
   {state.draft && <p>Draft quiz: Version {state.draft.version} - not available to Students</p>}
   <div className={styles.revisionActions}>
    {state.current && <Button variant="secondary" disabled={busy} onClick={() => setReview('current')}>Review Existing Quiz</Button>}
    <Button disabled={busy} onClick={() => setConfirm('generate')}>Regenerate Quiz from Latest Lesson</Button>
    {state.draft && <Button variant="secondary" disabled={busy} onClick={() => setReview('draft')}>Review Quiz Draft</Button>}
    {state.draft && <Button disabled={busy || review !== 'draft'} onClick={() => setConfirm('approve')}>Approve Quiz</Button>}
   </div>
   {quiz && <section aria-label={review === 'draft' ? 'Quiz draft questions' : 'Current quiz questions'}><h3>Quiz version {quiz.version}</h3><ol>{quiz.questions.map(q => <li key={q.number} className={styles.revisionText}><p>{q.question}</p><ul>{['A', 'B', 'C', 'D'].map(letter => <li key={letter}>{letter}. {q['option' + letter]}{q.correctAnswer === letter ? ' (correct answer)' : ''}</li>)}</ul></li>)}</ol><Button variant="secondary" onClick={() => setReview(null)}>Close Review</Button></section>}
  </>}
  {busy && <p role="status">{confirm === 'generate' ? 'Generating quiz draft...' : 'Approving quiz...'}</p>}
  {confirm && <Dialog className={styles.confirmDialog} labelledBy="quiz-confirm-title" describedBy="quiz-confirm-message" busy={busy} onClose={() => setConfirm(null)} initialFocus="button">
   <h2 id="quiz-confirm-title">{confirm === 'generate' ? 'Regenerate Quiz?' : 'Approve Quiz?'}</h2>
   <p id="quiz-confirm-message">{confirm === 'generate' ? 'Generate a new draft from the latest published lesson? The current quiz remains available until you approve the replacement.' : 'Make this reviewed quiz the current version for new attempts? Existing attempts keep their original questions and scores.'}</p>
   {error && <p role="alert">{error}</p>}
   <div className={styles.revisionActions}><Button variant="secondary" disabled={busy} onClick={() => setConfirm(null)}>Cancel</Button><Button disabled={busy} onClick={() => run(confirm)}>{busy ? 'Processing...' : confirm === 'generate' ? 'Generate Draft' : 'Approve Quiz Version'}</Button></div>
  </Dialog>}
 </section>;
}
