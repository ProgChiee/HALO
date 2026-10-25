import { useId, useRef, useState } from 'react';
import { BookOpen, ClipboardCheck, History } from 'lucide-react';
import styles from '../styles/LessonEditor.module.css';

const tabs = [['lesson', 'Lesson', BookOpen], ['assessment', 'Assessment', ClipboardCheck], ['history', 'Revision History', History]];

// Retain editable panels. Reopen read-only history fresh after revision actions.
export default function LessonWorkspaceTabs({ children, assessment, history }) {
  const id = useId();
  const buttons = useRef([]);
  const [active, setActive] = useState('lesson');
  const [visited, setVisited] = useState(['lesson']);
  function select(key) {
    setActive(key);
    setVisited(previous => previous.includes(key) ? previous : [...previous, key]);
  }
  function navigate(event, index) {
    const next = event.key === 'ArrowRight' ? (index + 1) % tabs.length
      : event.key === 'ArrowLeft' ? (index + tabs.length - 1) % tabs.length
        : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : null;
    if (next === null) return;
    event.preventDefault();
    select(tabs[next][0]);
    buttons.current[next]?.focus();
  }
  const panels = { lesson: children, assessment, history };
  return <>
    <div className={styles.workspaceTabs} role="tablist" aria-label="Lesson management">
      {tabs.map(([key, label, Icon], index) => <button key={key} type="button" role="tab"
        id={`${id}-${key}-tab`} aria-controls={`${id}-${key}-panel`} aria-selected={active === key}
        tabIndex={active === key ? 0 : -1} ref={node => { buttons.current[index] = node; }}
        className={`${styles.workspaceTab} ${active === key ? styles.tabActive : ''}`}
        onClick={() => select(key)} onKeyDown={event => navigate(event, index)}>
        <Icon size={18} aria-hidden="true" /><span>{label}</span>
      </button>)}
    </div>
    {tabs.map(([key]) => <div key={key} role="tabpanel" id={`${id}-${key}-panel`}
      aria-labelledby={`${id}-${key}-tab`} tabIndex={0} hidden={active !== key} className={styles.workspacePanel}>
      {visited.includes(key) && (key !== 'history' || active === key) ? panels[key] : null}
    </div>)}
  </>;
}
