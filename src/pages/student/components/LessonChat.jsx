import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Bot, Send, Volume2, VolumeX } from 'lucide-react';
import StudentPageShell from './StudentPageShell';
import Button from '../../../components/shared/Button';
import { getWeekLesson } from '../../../services/student/studentService';
import { openSession, getConversation, getExchange, sendMessage as sendMentorMessage } from '../../../services/student/aiMentorService';
import { useTextToSpeech } from '../../../hooks/useTextToSpeech';
import { useToast } from '../../../context/notifications/useToast';
import { loadLessonChat } from '../../../utils/lessonLoader';
import { lessonErrorMessage } from '../../../utils/lessonErrors';
import styles from '../styles/LessonChat.module.css';

// Route weekId resolves to the approved module ID before opening its mentor session.
export default function LessonChat() {
  const { topicId, weekId } = useParams();
  return <ModuleLessonChat key={String(topicId) + ':' + String(weekId)} topicId={topicId} weekId={weekId} />;
}

function ModuleLessonChat({ topicId, weekId }) {
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [mentorLoading, setMentorLoading] = useState(false);
  const [lesson, setLesson] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [beforeId, setBeforeId] = useState(null);
  const [olderLoading, setOlderLoading] = useState(false);
  const [olderError, setOlderError] = useState('');
  const olderLock = useRef(false);
  const prepending = useRef(false);
  const [draft, setDraft] = useState('');
  const draftError = draft.length > 4000
    ? 'Your message is too long. Shorten it to 4,000 characters or fewer.'
    : draft.length > 0 && !draft.trim() ? 'Enter a message; spaces alone cannot be sent.' : '';
  const [isAiTyping, setIsAiTyping] = useState(false);
  const messagesEndRef = useRef(null);
  const requestScope = useRef(null);
  const sendLock = useRef(false);
  const pendingSend = useRef(null);
  const { speak, stop, speakingId, isSupported: ttsSupported } = useTextToSpeech();
  const { showToast } = useToast();

  useEffect(() => {
    return () => stop();
  }, [topicId, weekId, stop]);

  useEffect(() => {
    const controller = new AbortController();
    requestScope.current = controller;
    sendLock.current = false;
    loadLessonChat({
      subjectId: topicId, weekId, signal: controller.signal, getLesson: getWeekLesson, openMentor: openSession,
      onCanonical: module => navigate(`/student/lesson/${module.subjectId}/${module.weekId}`, { replace: true }),
      onLesson: (module) => {
        setLesson(module);
        setIsLoading(false);
        setMentorLoading(true);
      },
      onMentor: (conversation) => {
        setBeforeId(conversation.hasOlder ? conversation.nextBeforeId : null);
        setSessionId(conversation.sessionId);
        setMentorLoading(false);
        setMessages(conversation.messages.map((m) => ({
          id: m.id, sender: m.sender === 'STUDENT' ? 'user' : 'ai', text: m.message,
        })));
      },
      onError: (error, stage) => {
        setLoadError(lessonErrorMessage(error, stage));
        setIsLoading(false);
        setMentorLoading(false);
      },
    });
    return () => { controller.abort(); };
  }, [topicId, weekId, reloadKey, navigate]);

  useEffect(() => {
    if (prepending.current) { prepending.current = false; return; }
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isAiTyping]);

  if (isLoading) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading lesson...</p>
        </div>
      </StudentPageShell>
    );
  }

  function retryLesson() {
    setBeforeId(null);
    setOlderLoading(false);
    setOlderError('');
    olderLock.current = false;
    setIsLoading(true);
    setLoadError('');
    setMentorLoading(false);
    setIsAiTyping(false);
    setLesson(null);
    setSessionId(null);
    setMessages([]);
    setReloadKey((key) => key + 1);
  }

  if (loadError && !lesson) {
    return (
      <StudentPageShell>
        <div className={styles.contentArea}>
          <main className={styles.notFound}>
            <p role="alert">{loadError}</p>
            <Button onClick={retryLesson}>Retry</Button>
            <Button onClick={() => navigate('/student/subjects')}>Back to Subjects</Button>
          </main>
        </div>
      </StudentPageShell>
    );
  }

  async function loadOlder() {
    const scope = requestScope.current;
    if (!beforeId || olderLock.current || !scope || scope.signal.aborted) return;
    olderLock.current = true; setOlderLoading(true); setOlderError('');
    try {
      const page = await getConversation(sessionId, beforeId, { signal: scope.signal });
      if (scope.signal.aborted || requestScope.current !== scope) return;
      if (page.sessionId !== sessionId || page.moduleId !== lesson.id || !Array.isArray(page.messages)) throw new Error('Invalid conversation page');
      prepending.current = true;
      setMessages(current => {
        const ids = new Set(current.map(message => message.id));
        const older = page.messages.filter(message => {
          if (ids.has(message.id)) return false;
          ids.add(message.id); return true;
        }).map(m => ({ id: m.id, sender: m.sender === 'STUDENT' ? 'user' : 'ai', text: m.message }));
        return [...older, ...current];
      });
      setBeforeId(page.hasOlder ? page.nextBeforeId : null);
    } catch {
      if (!scope.signal.aborted && requestScope.current === scope) setOlderError('Could not load older messages. Please try again.');
    } finally {
      if (!scope.signal.aborted && requestScope.current === scope) { olderLock.current = false; setOlderLoading(false); }
    }
  }

  async function handleSend(e) {
    e.preventDefault();
    if (sendLock.current) return;

    const text = draft.trim();
    if (!text || draft.length > 4000) {
      showToast(draftError || 'Enter a message before sending.', 'error');
      return;
    }
    if (!sessionId) return;

    const scope = requestScope.current;
    if (!scope || scope.signal.aborted) return;
    sendLock.current = true;
    if (!pendingSend.current || pendingSend.current.text !== text || pendingSend.current.sessionId !== sessionId) {
      pendingSend.current = { text, sessionId, requestId: crypto.randomUUID() };
    }
    const requestId = pendingSend.current.requestId;
    const userMessage = { id: crypto.randomUUID(), sender: 'user', text };
    setMessages((prev) => [...prev, userMessage]);
    setDraft('');
    setIsAiTyping(true);

    try {
      const result = await sendMentorMessage(sessionId, text, { signal: scope.signal, requestId });
      if (scope.signal.aborted || requestScope.current !== scope) return;
      if (typeof result?.haloMessage !== 'string' || !result.haloMessage.trim()) throw Object.assign(new Error('Empty reply'), { code: 'MENTOR_EMPTY_RESPONSE' });
      setMessages((prev) => [...prev, { id: crypto.randomUUID(), sender: 'ai', text: result.haloMessage }]);
      pendingSend.current = null;
    } catch (err) {
      if (!scope.signal.aborted && requestScope.current === scope) {
        const status = err?.response?.status;
        const uncertain = !status || status >= 500;
        if (uncertain) {
          try {
            const saved = await getExchange(sessionId, requestId, { signal: scope.signal });
            if (scope.signal.aborted || requestScope.current !== scope) return;
            if (saved.sessionId !== sessionId || saved.moduleId !== lesson.id || typeof saved.haloMessage !== 'string' || !saved.haloMessage.trim()) throw new Error('Invalid saved exchange', { cause: err });
            setMessages(prev => [...prev, { id: crypto.randomUUID(), sender: 'ai', text: saved.haloMessage }]);
            pendingSend.current = null;
            return;
          } catch {
            if (scope.signal.aborted || requestScope.current !== scope) return;
            // Retain the request ID: an in-flight original can still commit after this GET.
          }
        }
        setMessages((prev) => prev.filter((message) => message.id !== userMessage.id));
        setDraft(text);
        showToast(lessonErrorMessage(err, 'mentor'), 'error');
      }
    } finally {
      if (requestScope.current === scope) sendLock.current = false;
      if (!scope.signal.aborted && requestScope.current === scope) setIsAiTyping(false);
    }
  }

  return (
    <StudentPageShell>

      <div className={styles.contentArea}>
        <header className={styles.topHeader}>
          <div className={styles.mentorIdentity}>
            <span className={styles.mentorAvatar}>
              <Bot size={18} />
            </span>
            <div>
              <p className={styles.mentorName}>AI Mentor</p>
              <p className={styles.mentorStatus}>{mentorLoading ? 'Connecting AI Mentor...' : sessionId ? 'Ready to help' : 'AI Mentor unavailable'}</p>
            </div>
          </div>

          <div className={styles.breadcrumb}>
            <span className={styles.breadcrumbActive}>Week {lesson?.weekNumber}</span>
          </div>
        </header>

        <div className={styles.tabsRow}>
          <p className={styles.tabsSubtitle}>Ask about this module's uploaded materials</p>
          <div className={styles.tabsRowActions}>
            <Button onClick={() => navigate(`/student/quiz/${topicId}/${weekId}`)}>Start quiz</Button>
          </div>
        </div>

        <main className={styles.chatArea}>
          {lesson && (
            <section className={styles.bubble} aria-label="Published lesson">
              <h2>Published Lesson</h2>
              {[
                ['Objectives', lesson.generatedObjectives],
                ['Knowledge', lesson.generatedKnowledge],
                ['Examples', lesson.generatedExamples],
                ['Summary', lesson.generatedSummary],
              ].map(([title, content]) => (
                <section key={title}>
                  <h3>{title}</h3>
                  <p style={{ whiteSpace: 'pre-wrap' }}>{content}</p>
                </section>
              ))}
            </section>
          )}
          {loadError && (
            <div role="alert">
              <p>{loadError}</p>
              <Button onClick={retryLesson}>Retry mentor connection</Button>
            </div>
          )}
          {beforeId && <Button onClick={loadOlder} disabled={olderLoading}>{olderLoading ? 'Loading older messages...' : 'Load older messages'}</Button>}
          {olderError && <p role="alert">{olderError}</p>}
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`${styles.messageBlock} ${msg.sender === 'user' ? styles.messageBlockUser : ''}`}
            >
              <p className={styles.messageSender}>{msg.sender === 'ai' ? 'AI Mentor' : 'You'}</p>
              <div style={{ whiteSpace: 'pre-wrap' }} className={`${styles.bubble} ${msg.sender === 'user' ? styles.bubbleUser : styles.bubbleAi}`}>
                {msg.text}
              </div>

              {msg.sender === 'ai' && ttsSupported && (
                <button
                  type="button"
                  className={`${styles.speakBtn} ${speakingId === msg.id ? styles.speakBtnActive : ''}`}
                  onClick={() => speak(msg.id, msg.text)}
                  aria-label={speakingId === msg.id ? 'Stop reading message aloud' : 'Read message aloud'}
                >
                  {speakingId === msg.id ? <VolumeX size={14} /> : <Volume2 size={14} />}
                  {speakingId === msg.id ? 'Stop' : 'Listen'}
                </button>
              )}
            </div>
          ))}

          {isAiTyping && (
            <div className={styles.messageBlock}>
              <p className={styles.messageSender}>AI Mentor</p>
              <div className={`${styles.bubble} ${styles.bubbleAi} ${styles.bubbleTyping}`}>
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
                <span className={styles.typingDot} />
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </main>

        {draftError && <p id="mentor-draft-error" role="alert">{draftError}</p>}
        <form className={styles.inputRow} onSubmit={handleSend}>
          <input
            type="text"
            className={styles.chatInput}
            aria-label="Message AI Mentor"
            placeholder={isAiTyping ? 'Waiting for AI Mentor to respond...' : 'Ask AI Mentor'}
            value={draft}
            aria-invalid={Boolean(draftError)}
            aria-describedby={draftError ? 'mentor-draft-error' : undefined}
            onChange={(e) => setDraft(e.target.value)}
            disabled={isAiTyping || !sessionId}
          />
          <button type="submit" className={styles.sendBtn} aria-label="Send message" disabled={isAiTyping || !sessionId || !draft.trim() || draft.length > 4000}>
            <Send size={18} />
          </button>
        </form>
      </div>
    </StudentPageShell>
  );
}
