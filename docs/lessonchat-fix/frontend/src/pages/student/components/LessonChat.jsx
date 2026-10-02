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
import { lessonErrorMessage } from '../../../utils/lessonErrors';
import styles from '../styles/LessonChat.module.css';

// Route weekId resolves to the approved module ID before opening its mentor session.
export default function LessonChat() {
  const { topicId, weekId } = useParams();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [lesson, setLesson] = useState(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [isAiTyping, setIsAiTyping] = useState(false);
  const messagesEndRef = useRef(null);
  const isMountedRef = useRef(true);
  const sendLock = useRef(false);
  const { speak, stop, speakingId, isSupported: ttsSupported } = useTextToSpeech();
  const { showToast } = useToast();

  useEffect(() => {
    isMountedRef.current = true;
    return () => { isMountedRef.current = false; };
  }, []);

  useEffect(() => {
    return () => stop();
  }, [topicId, weekId, stop]);

  useEffect(() => {
    let isMounted = true;
    const controller = new AbortController();

    async function loadLesson() {
      try {
        const module = await getWeekLesson(weekId, { signal: controller.signal });
        if (!isMounted) return;

        if (String(module.weekId) !== String(weekId)) throw new Error('Lesson/week mismatch');
        setLesson(module);
        const conversation = await openSession(module.id, { signal: controller.signal });
        if (String(conversation.moduleId) !== String(module.id) || !conversation.sessionId) {
          throw new Error('Mentor/module mismatch');
        }
        if (!isMounted) return;

        setSessionId(conversation.sessionId);
        setMessages(
          (conversation.messages ?? []).map((m) => ({
            id: m.id,
            sender: m.sender === 'STUDENT' ? 'user' : 'ai', // adjust if MessageSender enum values differ
            text: m.message,
          }))
        );
      } catch (err) {
        if (isMounted) setLoadError(lessonErrorMessage(err));
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadLesson();
    return () => { isMounted = false; controller.abort(); };
  }, [weekId, reloadKey]);

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

    sendLock.current = true;
    const userMessage = { id: crypto.randomUUID(), sender: 'user', text };
    setMessages((prev) => [...prev, userMessage]);
    setDraft('');
    setIsAiTyping(true);

    try {
      const result = await sendMentorMessage(sessionId, text);
      if (!isMountedRef.current) return;
      setMessages((prev) => [...prev, { id: crypto.randomUUID(), sender: 'ai', text: result.haloMessage }]);
    } catch (err) {
      if (isMountedRef.current) {
        setMessages((prev) => prev.filter((message) => message.id !== userMessage.id));
        setDraft(text);
        showToast(lessonErrorMessage(err), 'error');
      }
    } finally {
      sendLock.current = false;
      if (isMountedRef.current) setIsAiTyping(false);
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
              <p className={styles.mentorStatus}>Ready to help</p>
            </div>
          </div>

          <div className={styles.breadcrumb}>
            <span className={styles.breadcrumbActive}>Week {weekId}</span>
          </div>
        </header>

        <div className={styles.tabsRow}>
          <p className={styles.tabsSubtitle}>Chat with your AI Mentor</p>
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
              <div className={`${styles.bubble} ${msg.sender === 'user' ? styles.bubbleUser : styles.bubbleAi}`}>
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