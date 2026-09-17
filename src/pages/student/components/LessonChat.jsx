import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Bot, Send, ChevronRight, Volume2, VolumeX } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getWeekLesson } from '../../../services/student/studentService';
import { openSession, sendMessage as sendMentorMessage } from '../../../services/student/aiMentorService';
import { useTextToSpeech } from '../../../hooks/useTextToSpeech';
import { useToast } from '../../../context/notifications/ToastContext';
import styles from '../styles/LessonChat.module.css';

// ⚠️ The URL still carries :topicId/:weekId (see AppRoutes.jsx), but the
// backend's mentor endpoints key off a moduleId, not weekId directly —
// so we first fetch the week's approved lesson (module) to get its id,
// then open the mentor session for THAT module id.
//
// Also: there's no subject/week title data available to students (see
// studentService.js's "NOT YET AVAILABLE" notes), so the breadcrumb below
// just shows "Week {weekId}" instead of a real subject/week name until
// your backend team adds a student-readable subjects/weeks endpoint.

export default function LessonChat() {
  const { topicId, weekId } = useParams();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [sessionId, setSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [isAiTyping, setIsAiTyping] = useState(false);
  const messagesEndRef = useRef(null);
  const isMountedRef = useRef(true);
  const { speak, stop, speakingId, isSupported: ttsSupported } = useTextToSpeech();
  const { showToast } = useToast();

  useEffect(() => {
    return () => { isMountedRef.current = false; };
  }, []);

  useEffect(() => {
    return () => stop();
  }, [topicId, weekId, stop]);

  useEffect(() => {
    let isMounted = true;

    async function loadLesson() {
      try {
        const module = await getWeekLesson(weekId);
        if (!isMounted) return;

        const conversation = await openSession(module.id);
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
        console.error(err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadLesson();
    return () => { isMounted = false; };
  }, [weekId]);

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

  if (loadError) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} />
        <div className={styles.contentArea}>
          <main className={styles.notFound}>
            <p>Couldn't load this lesson. Please refresh and try again.</p>
            <Button onClick={() => navigate('/student/subjects')}>Back to Subjects</Button>
          </main>
        </div>
      </div>
    );
  }

  async function handleSend(e) {
    e.preventDefault();
    if (isAiTyping) return;

    const text = draft.trim();
    if (!text || !sessionId) return;

    const userMessage = { id: Date.now(), sender: 'user', text };
    setMessages((prev) => [...prev, userMessage]);
    setDraft('');
    setIsAiTyping(true);

    try {
      const result = await sendMentorMessage(sessionId, text);
      if (!isMountedRef.current) return;
      setMessages((prev) => [...prev, { id: Date.now() + 1, sender: 'ai', text: result.haloMessage }]);
    } catch (err) {
      console.error(err);
      if (isMountedRef.current) {
        showToast("AI Mentor couldn't respond. Please try again.", 'error');
      }
    } finally {
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
            disabled={isAiTyping}
          />
          <button type="submit" className={styles.sendBtn} aria-label="Send message" disabled={isAiTyping}>
            <Send size={18} />
          </button>
        </form>
      </div>
    </div>
  );
}