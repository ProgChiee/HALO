import { useState, useRef, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Bot, Send, ChevronRight } from 'lucide-react';
import Sidebar from '../../../components/shared/Sidebar';
import Button from '../../../components/shared/Button';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { mockModuleProgress } from '../../../data/student/studentDashboardData';
import { getTopicAndWeek } from '../../../services/student/studentService';
import { getChatHistory, sendMessageToMentor } from '../../../services/student/aiMentorService';
import styles from '../styles/LessonChat.module.css';

export default function LessonChat() {
  const { topicId, weekId } = useParams();
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [topic, setTopic] = useState(null);
  const [week, setWeek] = useState(null);
  const [messages, setMessages] = useState([]);
  const [draft, setDraft] = useState('');
  const [isAiTyping, setIsAiTyping] = useState(false);
  const messagesEndRef = useRef(null);

  useEffect(() => {
    let isMounted = true;

    async function loadLesson() {
      const { topic: fetchedTopic, week: fetchedWeek } = await getTopicAndWeek(topicId, weekId);
      if (!isMounted) return;

      setTopic(fetchedTopic);
      setWeek(fetchedWeek);

      if (fetchedTopic && fetchedWeek) {
        const history = await getChatHistory(fetchedTopic.title, fetchedWeek.label);
        if (isMounted) setMessages(history);
      }

      if (isMounted) setIsLoading(false);
    }

    loadLesson();
    return () => { isMounted = false; };
  }, [topicId, weekId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isAiTyping]);

  if (isLoading) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={mockModuleProgress} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading lesson...</p>
        </div>
      </div>
    );
  }

  // If the topic/week id in the URL doesn't match any data, bail out gracefully
  if (!topic || !week) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={mockModuleProgress} />
        <div className={styles.contentArea}>
          <main className={styles.notFound}>
            <p>We couldn't find that lesson.</p>
            <Button onClick={() => navigate('/student/subjects')}>Back to Subjects</Button>
          </main>
        </div>
      </div>
    );
  }

  async function handleSend(e) {
    e.preventDefault();
    const text = draft.trim();
    if (!text) return;

    const userMessage = { id: Date.now(), sender: 'user', text };
    setMessages((prev) => [...prev, userMessage]);
    setDraft('');
    setIsAiTyping(true);

    const reply = await sendMessageToMentor(text, weekId);
    setMessages((prev) => [...prev, { id: Date.now() + 1, sender: 'ai', text: reply }]);
    setIsAiTyping(false);
  }

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={mockModuleProgress} />

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
            <span className={styles.breadcrumbMuted}>Module 1</span>
            <ChevronRight size={14} />
            <span className={styles.breadcrumbActive}>{week.label}: {topic.title}</span>
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
            placeholder="Ask AI Mentor"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
          />
          <button type="submit" className={styles.sendBtn} aria-label="Send message">
            <Send size={18} />
          </button>
        </form>
      </div>
    </div>
  );
}