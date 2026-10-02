import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Bot, Send, Volume2, VolumeX } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getWeekLesson } from '../../../services/student/studentService';
import { openSession, sendMessage as sendMentorMessage } from '../../../services/student/aiMentorService';
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
  const [draft, setDraft] = useState('');
  const [isAiTyping, setIsAiTyping] = useState(false);
  const messagesEndRef = useRef(null);
  const requestScope = useRef(null);
  const sendLock = useRef(false);
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
      weekId, signal: controller.signal, getLesson: getWeekLesson, openMentor: openSession,
      onLesson: (module) => {
        setLesson(module);
        setIsLoading(false);
        setMentorLoading(true);
      },
      onMentor: (conversation) => {
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
  }, [topicId, weekId, reloadKey]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isAiTyping]);

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading lesson...</p>
        </div>
      </div>
    );
  }

  function retryLesson() {
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
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <main className={styles.notFound}>
            <p role="alert">{loadError}</p>
            <Button onClick={retryLesson}>Retry</Button>
            <Button onClick={() => navigate('/student/subjects')}>Back to Subjects</Button>
          </main>
        </div>
      </div>
    );
  }

  async function handleSend(e) {
    e.preventDefault();
    if (sendLock.current) return;

    const text = draft.trim();
    if (!text || !sessionId) return;

    const scope = requestScope.current;
    if (!scope || scope.signal.aborted) return;
    sendLock.current = true;
    const userMessage = { id: crypto.randomUUID(), sender: 'user', text };
    setMessages((prev) => [...prev, userMessage]);
    setDraft('');
    setIsAiTyping(true);

    try {
      const result = await sendMentorMessage(sessionId, text, { signal: scope.signal });
      if (scope.signal.aborted || requestScope.current !== scope) return;
      if (typeof result?.haloMessage !== 'string' || !result.haloMessage.trim()) throw Object.assign(new Error('Empty reply'), { code: 'MENTOR_EMPTY_RESPONSE' });
      setMessages((prev) => [...prev, { id: crypto.randomUUID(), sender: 'ai', text: result.haloMessage }]);
    } catch (err) {
      if (!scope.signal.aborted && requestScope.current === scope) {
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
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} />

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
            <span className={styles.breadcrumbActive}>Week {weekId}</span>
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

        <form className={styles.inputRow} onSubmit={handleSend}>
          <input
            type="text"
            className={styles.chatInput}
            placeholder={isAiTyping ? 'Waiting for AI Mentor to respond...' : 'Ask AI Mentor'}
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            disabled={isAiTyping || !sessionId}
          />
          <button type="submit" className={styles.sendBtn} aria-label="Send message" disabled={isAiTyping || !sessionId}>
            <Send size={18} />
          </button>
        </form>
      </div>
    </div>
  );
}