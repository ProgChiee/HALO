import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ClipboardCheck, CheckCircle2, XCircle, RotateCcw } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { useToast } from '../../../context/notifications/ToastContext';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getTopicAndWeek, markLessonComplete, getOverallModuleProgress } from '../../../services/student/studentService';
import { getQuiz, submitQuiz } from '../../../services/student/quizService';
import styles from '../styles/Quiz.module.css';

export default function Quiz() {
  const { topicId, weekId } = useParams();
  const navigate = useNavigate();
  const { showToast } = useToast();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [topic, setTopic] = useState(null);
  const [week, setWeek] = useState(null);
  const [quiz, setQuiz] = useState(null);
  const [moduleProgress, setModuleProgress] = useState(null);

  const [stage, setStage] = useState('intro'); // 'intro' | 'active' | 'results'
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedAnswers, setSelectedAnswers] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    let isMounted = true;

    async function loadQuiz() {
      setIsLoading(true);
      setLoadError(false);
      try {
        const [{ topic: fetchedTopic, week: fetchedWeek }, fetchedQuiz, moduleProg] = await Promise.all([
          getTopicAndWeek(topicId, weekId),
          getQuiz(topicId, weekId),
          getOverallModuleProgress(),
        ]);
        if (isMounted) {
          setTopic(fetchedTopic);
          setWeek(fetchedWeek);
          setQuiz(fetchedQuiz);
          setModuleProgress(moduleProg);
        }
      } catch (err) {
        console.error(err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadQuiz();
    return () => { isMounted = false; };
  }, [topicId, weekId]);

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={moduleProgress} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading quiz...</p>
        </div>
      </div>
    );
  }

  if (loadError || !quiz) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={moduleProgress} />
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

  const currentQuestion = quiz.questions[currentIndex];
  const isLastQuestion = currentIndex === quiz.questions.length - 1;
  const hasAnsweredCurrent = selectedAnswers[currentQuestion?.id] !== undefined;

  function handleStart() {
    setStage('active');
    setCurrentIndex(0);
    setSelectedAnswers({});
  }

  function handleSelectOption(optionIndex) {
    setSelectedAnswers((prev) => ({ ...prev, [currentQuestion.id]: optionIndex }));
  }

  async function handleNext() {
    if (isLastQuestion) {
      if (isSubmitting) return; // block double-submit (e.g. double-click on "Submit quiz")

      setIsSubmitting(true);
      try {
        const score = quiz.questions.reduce(
          (total, q) => total + (selectedAnswers[q.id] === q.correctIndex ? 1 : 0),
          0
        );
        await submitQuiz(topicId, weekId, score, quiz.questions.length);

        // Mark the week complete (unlocking the next one) only when the
        // student passes — matches the 70% threshold shown on the
        // results screen below. Without this, the Subjects page's
        // done/now/locked progression never actually advances no matter
        // how many quizzes are taken.
        const passed = Math.round((score / quiz.questions.length) * 100) >= 70;
        if (passed) {
          await markLessonComplete(topicId, weekId);
          // Refresh the Sidebar's module progress right away, so the
          // student sees the updated percentage on this results screen
          // instead of only after navigating to a different page.
          const updatedProgress = await getOverallModuleProgress();
          setModuleProgress(updatedProgress);
        }

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
  }

  const score = quiz.questions.reduce(
    (total, q) => total + (selectedAnswers[q.id] === q.correctIndex ? 1 : 0),
    0
  );
  const scorePercent = Math.round((score / quiz.questions.length) * 100);

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={moduleProgress} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <ClipboardCheck size={16} />
            {quiz.title}
          </div>
          {topic && week && (
            <span className={styles.lessonTag}>{week.label}: {topic.title}</span>
          )}
        </header>

        <main className={styles.main}>
          {stage === 'intro' && (
            <div className={styles.introCard}>
              <span className={styles.introIcon}>
                <ClipboardCheck size={28} />
              </span>
              <h1 className={styles.introTitle}>{quiz.title}</h1>
              <p className={styles.introMeta}>{quiz.questions.length} questions · Multiple choice</p>
              <p className={styles.introDescription}>
                Answer each question to the best of your ability. You can review your score
                and correct answers once you finish.
              </p>
              <Button onClick={handleStart}>Start quiz</Button>
            </div>
          )}

          {stage === 'active' && currentQuestion && (
            <div className={styles.quizCard}>
              <div className={styles.progressRow}>
                <span className={styles.progressText}>
                  Question {currentIndex + 1} of {quiz.questions.length}
                </span>
                <div className={styles.progressTrack}>
                  <div
                    className={styles.progressFill}
                    style={{ width: `${((currentIndex + 1) / quiz.questions.length) * 100}%` }}
                  />
                </div>
              </div>

              <h2 className={styles.questionText}>{currentQuestion.question}</h2>

              <div className={styles.optionsList}>
                {currentQuestion.options.map((option, index) => {
                  const isSelected = selectedAnswers[currentQuestion.id] === index;
                  return (
                    <button
                      key={index}
                      className={`${styles.optionRow} ${isSelected ? styles.optionRowSelected : ''}`}
                      onClick={() => handleSelectOption(index)}
                    >
                      <span className={styles.optionMarker}>{String.fromCharCode(65 + index)}</span>
                      {option}
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

          {stage === 'results' && (
            <div className={styles.resultsCard}>
              <span className={styles.resultsIcon}>
                {scorePercent >= 70 ? <CheckCircle2 size={32} /> : <XCircle size={32} />}
              </span>
              <h1 className={styles.resultsScore}>{score}/{quiz.questions.length}</h1>
              <p className={styles.resultsPercent}>{scorePercent}% correct</p>
              <p className={styles.resultsMessage}>
                {scorePercent >= 70
                  ? "Great job! You've passed this quiz."
                  : "Don't worry — review the lesson and try again."}
              </p>

              <div className={styles.reviewList}>
                {quiz.questions.map((q, i) => {
                  const isCorrect = selectedAnswers[q.id] === q.correctIndex;
                  return (
                    <div key={q.id} className={styles.reviewRow}>
                      <span className={isCorrect ? styles.reviewIconCorrect : styles.reviewIconWrong}>
                        {isCorrect ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
                      </span>
                      <span className={styles.reviewText}>Q{i + 1}: {q.question}</span>
                    </div>
                  );
                })}
              </div>

              <div className={styles.actionsRow}>
                <Button variant="secondary" onClick={handleRetry}>
                  <RotateCcw size={16} style={{ marginRight: 6 }} />
                  Retry quiz
                </Button>
                <Button onClick={() => navigate('/student')}>Back to Dashboard</Button>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}