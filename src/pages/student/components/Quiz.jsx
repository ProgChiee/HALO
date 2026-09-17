import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ClipboardCheck, CheckCircle2, XCircle, RotateCcw } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getWeekLesson } from '../../../services/student/studentService';
import { getAssessmentStatus, getAssessment, startAttempt, submitAttempt } from '../../../services/student/quizService';
import styles from '../styles/Quiz.module.css';

// ⚠️ Redesigned for the real attempt-based backend flow. Key differences
// from the old mock quiz:
// - Scoring happens SERVER-SIDE. The backend never sends correct answers
//   to the frontend before submission — but submitAttempt() DOES return
//   full per-question feedback (studentAnswer, correctAnswer, correct)
//   once you've submitted, so the review screen below uses that.
// - "Mark lesson complete" is NOT called explicitly here anymore — per
//   studentService.js's notes, module completion (StudentModuleProgressResponse)
//   is expected to be set automatically server-side when an attempt passes.
//   Confirm this assumption with your backend team.
// - Options come back as optionA/B/C/D strings (not an array), and
//   answers are submitted by LETTER ("A"/"B"/"C"/"D"), not index.

const OPTION_KEYS = ['A', 'B', 'C', 'D'];

export default function Quiz() {
  const { topicId, weekId } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [moduleId, setModuleId] = useState(null);
  const [status, setStatus] = useState(null); // AssessmentStatusResponse
  const [assessment, setAssessment] = useState(null);
  const [attemptId, setAttemptId] = useState(null);
  const [result, setResult] = useState(null); // { attemptId, score, passed }

  const [stage, setStage] = useState('intro'); // 'intro' | 'active' | 'results'
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedAnswers, setSelectedAnswers] = useState({}); // { [questionId]: 'A' | 'B' | 'C' | 'D' }
  const [isStarting, setIsStarting] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let isMounted = true;

    async function loadQuiz() {
      setIsLoading(true);
      setLoadError(false);
      try {
        const module = await getWeekLesson(weekId);
        if (!isMounted) return;
        setModuleId(module.id);

        const [assessmentStatus, fetchedAssessment] = await Promise.all([
          getAssessmentStatus(module.id),
          getAssessment(module.id),
        ]);
        if (!isMounted) return;

        setStatus(assessmentStatus);
        setAssessment(fetchedAssessment);
      } catch (err) {
        console.error(err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadQuiz();
    return () => { isMounted = false; };
  }, [weekId]);

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading quiz...</p>
        </div>
      </div>
    );
  }

  if (loadError || !assessment) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <div className={styles.introCard}>
            <p className={styles.introDescription}>
              Couldn't load this quiz. Please check your connection and try again.
            </p>
            <Button onClick={() => navigate(0)}>Retry</Button>
          </div>
        </div>
      </div>
    );
  }

  const currentQuestion = assessment.questions[currentIndex];
  const isLastQuestion = currentIndex === assessment.questions.length - 1;
  const hasAnsweredCurrent = selectedAnswers[currentQuestion?.id] !== undefined;

  async function handleStart() {
    if (isStarting) return;
    setIsStarting(true);
    try {
      const attempt = await startAttempt(moduleId);
      setAttemptId(attempt.attemptId);
      setStage('active');
      setCurrentIndex(0);
      setSelectedAnswers({});
    } catch (err) {
      console.error(err);
      showToast("Couldn't start the quiz. Please try again.", 'error');
    } finally {
      setIsStarting(false);
    }
  }

  function handleSelectOption(letter) {
    setSelectedAnswers((prev) => ({ ...prev, [currentQuestion.id]: letter }));
  }

  async function handleNext() {
    if (isLastQuestion) {
      if (isSubmitting) return;
      setIsSubmitting(true);
      try {
        const answers = assessment.questions.map((q) => ({
          questionId: q.id,
          answer: selectedAnswers[q.id],
        }));
        const submitted = await submitAttempt(attemptId, answers);
        setResult(submitted);
        showToast('Quiz submitted!', 'success');
        setStage('results');
      } catch (err) {
        console.error(err);
        showToast("Couldn't submit your quiz. Please try again.", 'error');
      } finally {
        setIsSubmitting(false);
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
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <ClipboardCheck size={16} />
            {assessment.title}
          </div>
          <span className={styles.lessonTag}>Week {weekId}</span>
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

              <Button onClick={handleStart} isLoading={isStarting} disabled={status && !status.canTakeAssessment}>
                {status?.hasUnfinishedAttempt ? 'Resume quiz' : 'Start quiz'}
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
                      onClick={() => handleSelectOption(letter)}
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
              <h1 className={styles.resultsScore}>{result.score}%</h1>
              <p className={styles.resultsMessage}>
                {result.passed
                  ? "Great job! You've passed this quiz."
                  : "Don't worry — review the lesson and try again."}
              </p>

              {/* Per-question review — the backend's submitAttempt() DOES
                  include this in result.feedback. */}
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
                {!result.passed && (
                  <Button variant="secondary" onClick={handleRetry}>
                    <RotateCcw size={16} style={{ marginRight: 6 }} />
                    Retry quiz
                  </Button>
                )}
                <Button onClick={() => navigate('/student')}>Back to Dashboard</Button>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}