import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { logStudentError } from '../../../utils/studentDiagnostics';
import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ClipboardCheck, CheckCircle2, XCircle, RotateCcw } from 'lucide-react';
import StudentPageShell from './StudentPageShell';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/useToast';
import { buildAssessmentAnswers } from '../../../utils/backendContract';
import { shouldReconcileQuiz, isSavedQuizResult } from '../../../utils/quizReconciliation';
import { validateLessonRoute } from '../../../utils/lessonLoader';
import styles from '../styles/Quiz.module.css';

// The server scores A/B/C/D answers and completes module progress on a passing attempt.
const OPTION_KEYS = ['A', 'B', 'C', 'D'];

export default function Quiz() {
  const { topicId, weekId } = useParams();
  const { identity } = useStudentWorkflow();
  return <WeekQuiz key={`${identity}:${topicId}:${weekId}`} subjectId={topicId} weekId={weekId} />;
}

function WeekQuiz({ subjectId, weekId }) {
  const workflow = useStudentWorkflow();
  const { getWeekLesson, getAssessmentStatus, getAssessment, startAttempt, submitAttempt, getAttemptResult } = workflow.api;
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [moduleId, setModuleId] = useState(null);
  const [weekNumber, setWeekNumber] = useState(null);
  const [status, setStatus] = useState(null); // AssessmentStatusResponse
  const [assessment, setAssessment] = useState(null);
  const [attemptId, setAttemptId] = useState(null);
  const [result, setResult] = useState(null); // { attemptId, score, passed }

  const [stage, setStage] = useState('intro'); // 'intro' | 'active' | 'results'
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedAnswers, setSelectedAnswers] = useState({}); // { [questionId]: 'A' | 'B' | 'C' | 'D' }
  const [isStarting, setIsStarting] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const actionLock = useRef(false);
  const mounted = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let isMounted = true;
    const controller = new AbortController();
    const config = { signal: controller.signal };

    async function loadQuiz() {
      setIsLoading(true);
      setLoadError(false);
      try {
        const module = await getWeekLesson(weekId, config);
        if (!isMounted) return;
        validateLessonRoute(module, weekId);
        if (String(module.subjectId) !== String(subjectId)) {
          navigate(`${workflow.basePath}/quiz/${module.subjectId}/${module.weekId}`, { replace: true }); return;
        }
        setWeekNumber(module.weekNumber);
        setModuleId(module.id);

        const assessmentStatus = await getAssessmentStatus(module.id, config);
        if (!isMounted) return;
        setStatus(assessmentStatus);
        if (assessmentStatus.assessmentExists && assessmentStatus.assessmentAvailable) {
          const fetchedAssessment = await getAssessment(module.id, config);
          if (!isMounted) return;
          setAssessment(fetchedAssessment);
        }
      } catch (err) {
        logStudentError('load-quiz', err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadQuiz();
    return () => { isMounted = false; controller.abort(); };
  }, [subjectId, weekId, navigate, workflow.basePath, getWeekLesson, getAssessmentStatus, getAssessment]);

  if (isLoading) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading quiz...</p>
        </div>
      </StudentPageShell>
    );
  }

  if (loadError) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <div className={styles.introCard}>
            <p className={styles.introDescription}>
              Couldn't load this quiz. Please check your connection and try again.
            </p>
            <Button onClick={() => navigate(0)}>Retry</Button>
          </div>
        </div>
      </StudentPageShell>
    );
  }

  if (!assessment?.questions?.length) {
    return <StudentPageShell>
      <main className={styles.contentArea}>
        <p className={styles.loadingText}>This quiz is not available yet.</p>
        <Button onClick={() => navigate(workflow.basePath + '/subjects')}>Back to Subjects</Button>
      </main>
    </StudentPageShell>;
  }

  const currentQuestion = assessment.questions[currentIndex];
  const isLastQuestion = currentIndex === assessment.questions.length - 1;
  const hasAnsweredCurrent = selectedAnswers[currentQuestion?.id] !== undefined;

  async function handleStart() {
    if (actionLock.current || !status?.canTakeAssessment) return;
    actionLock.current = true;
    setIsStarting(true);
    try {
      const latestStatus = await getAssessmentStatus(moduleId);
      if (!mounted.current) return;
      setStatus(latestStatus);
      if (!latestStatus.canTakeAssessment) return;
      const attempt = await startAttempt(moduleId);
      if (!mounted.current) return;
      setAttemptId(attempt.attemptId);
      setStage('active');
      setCurrentIndex(0);
      setSelectedAnswers({});
    } catch (err) {
      logStudentError('start-quiz', err);
      if (mounted.current) showToast("Couldn't start the quiz. Please try again.", 'error');
    } finally {
      actionLock.current = false;
      if (mounted.current) setIsStarting(false);
    }
  }

  function handleSelectOption(letter) {
    if (actionLock.current) return;
    setSelectedAnswers((prev) => ({ ...prev, [currentQuestion.id]: letter }));
  }

  async function handleNext() {
    if (actionLock.current || !hasAnsweredCurrent) return;
    if (isLastQuestion) {
      actionLock.current = true;
      setIsSubmitting(true);
      try {
        const answers = buildAssessmentAnswers(assessment.questions, selectedAnswers);
        const submitted = await submitAttempt(attemptId, answers);
        if (!mounted.current) return;
        setResult(submitted);
        showToast(workflow.preview ? 'Preview evaluated. No Student records changed.' : 'Quiz submitted!', 'success');
        setStage('results');
        // The submit controller fills score/passed only. Detailed feedback
        // comes from the separate result endpoint.
        try {
          const review = await getAttemptResult(submitted.attemptId);
          if (mounted.current) setResult(review);
        } catch {
          if (mounted.current) showToast(workflow.preview ? 'Preview evaluated, but the answer review could not load.' : 'Score saved, but the answer review could not load.', 'error');
        }
        try {
          const latestStatus = await getAssessmentStatus(moduleId);
          if (mounted.current) setStatus(latestStatus);
        } catch {
          // The submitted result remains authoritative; start rechecks eligibility.
        }
      } catch (err) {
        logStudentError('submit-quiz', err);
        if (!mounted.current) return;
        if (shouldReconcileQuiz(err)) {
          try {
            const saved = await getAttemptResult(attemptId);
            if (!mounted.current) return;
            if (!isSavedQuizResult(saved, attemptId)) throw new Error('Invalid saved result', { cause: err });
            setResult(saved);
            setStage('results');
            showToast(workflow.preview ? 'Preview evaluated. No Student records changed.' : 'Quiz submitted!', 'success');
          } catch (reconcileError) {
            if (!mounted.current) return;
            logStudentError('submit-quiz', reconcileError);
            const unsubmitted = reconcileError?.response?.data?.code === 'ASSESSMENT_NOT_SUBMITTED';
            showToast(unsubmitted ? 'Your quiz has not been submitted yet. Please try again.'
              : 'Could not confirm your submission. Please check your connection and try again.', 'error');
          }
        } else {
          showToast("Couldn't submit your quiz. Please try again.", 'error');
        }
      } finally {
        actionLock.current = false;
        if (mounted.current) setIsSubmitting(false);
      }
    } else {
      setCurrentIndex((i) => i + 1);
    }
  }

  function handleRetry() {
    setStage('intro');
    setResult(null);
    setAttemptId(null);
  }

  return (
    <StudentPageShell>

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <ClipboardCheck size={16} />
            {assessment.title}
          </div>
          <span className={styles.lessonTag}>Week {weekNumber}</span>
        </header>

        <main className={styles.main}>
          {stage === 'intro' && (
            <div className={styles.introCard}>
              <span className={styles.introIcon}>
                <ClipboardCheck size={28} />
              </span>
              <h1 className={styles.introTitle}>{assessment.title}</h1>
              <p className={styles.introMeta}>
                {assessment.questions.length} questions · Multiple choice · Pass with {assessment.passingScore}%
              </p>

              {status?.alreadyPassed ? (
                <p className={styles.introDescription}>
                  You've already passed this quiz.
                </p>
              ) : (
                <p className={styles.introDescription}>
                  Answer each question to the best of your ability. Your score is calculated after you submit.
                </p>
              )}

              {status?.hasUnfinishedAttempt && <p className={styles.introDescription}>
                {workflow.preview ? 'This is a temporary preview attempt. No Student progress or academic records will be changed.' : 'Your unfinished attempt will be reused. Answers are saved only when you submit.'}
              </p>}
              <Button onClick={handleStart} isLoading={isStarting} disabled={!status?.canTakeAssessment}>
                {status?.hasUnfinishedAttempt ? 'Continue attempt' : 'Start quiz'}
              </Button>
            </div>
          )}

          {stage === 'active' && currentQuestion && (
            <div className={styles.quizCard}>
              <div className={styles.progressRow}>
                <span className={styles.progressText}>
                  Question {currentIndex + 1} of {assessment.questions.length}
                </span>
                <div className={styles.progressTrack}>
                  <div
                    className={styles.progressFill}
                    style={{ width: `${((currentIndex + 1) / assessment.questions.length) * 100}%` }}
                  />
                </div>
              </div>

              <h2 className={styles.questionText}>{currentQuestion.questionText}</h2>

              <div className={styles.optionsList}>
                {OPTION_KEYS.map((letter) => {
                  const optionText = currentQuestion[`option${letter}`];
                  if (!optionText) return null;
                  const isSelected = selectedAnswers[currentQuestion.id] === letter;
                  return (
                    <button
                      key={letter}
                      className={`${styles.optionRow} ${isSelected ? styles.optionRowSelected : ''}`}
                      aria-pressed={isSelected}
                      disabled={isSubmitting} onClick={() => handleSelectOption(letter)}
                    >
                      <span className={styles.optionMarker}>{letter}</span>
                      {optionText}
                    </button>
                  );
                })}
              </div>

              <div className={styles.actionsRow}>
                <Button
                  onClick={handleNext}
                  disabled={!hasAnsweredCurrent || isSubmitting}
                  isLoading={isLastQuestion && isSubmitting}
                >
                  {isLastQuestion ? 'Submit quiz' : 'Next question'}
                </Button>
              </div>
            </div>
          )}

          {stage === 'results' && result && (
            <div className={styles.resultsCard}>
              <span className={styles.resultsIcon}>
                {result.passed ? <CheckCircle2 size={32} /> : <XCircle size={32} />}
              </span>
              <h1 className={styles.resultsScore}>{workflow.preview ? 'Preview Score: ' : ''}{result.score}%</h1>
              {workflow.preview && <p role="status">This was a preview attempt. No Student progress or academic records were changed.</p>}
              <p className={styles.resultsMessage}>
                {result.passed
                  ? "Great job! You've passed this quiz."
                  : "Don't worry — review the lesson and try again."}
              </p>

              {/* Per-question feedback is loaded from the result endpoint. */}
              {result.feedback && result.feedback.length > 0 && (
                <div className={styles.reviewList}>
                  <h2 className={styles.reviewTitle}>Review your answers</h2>
                  {result.feedback.map((item) => (
                    <div
                      key={item.questionId}
                      className={`${styles.reviewItem} ${item.correct ? styles.reviewItemCorrect : styles.reviewItemIncorrect}`}
                    >
                      <p className={styles.reviewQuestion}>
                        {item.questionNumber}. {item.questionText}
                      </p>
                      <p className={styles.reviewAnswer}>
                        Your answer: <strong>{item.studentAnswer}</strong>
                        {item.correct ? (
                          <CheckCircle2 size={14} className={styles.reviewIconCorrect} />
                        ) : (
                          <XCircle size={14} className={styles.reviewIconIncorrect} />
                        )}
                      </p>
                      {!item.correct && (
                        <p className={styles.reviewCorrectAnswer}>
                          Correct answer: <strong>{item.correctAnswer}</strong>
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}

              <div className={styles.actionsRow}>
                {(!result.passed || workflow.preview) && (
                  <Button variant="secondary" disabled={isSubmitting} onClick={handleRetry}>
                    <RotateCcw size={16} style={{ marginRight: 6 }} />
                    Retry quiz
                  </Button>
                )}
                <Button onClick={() => navigate(workflow.basePath)}>Back to Dashboard</Button>
              </div>
            </div>
          )}
        </main>
      </div>
    </StudentPageShell>
  );
}
