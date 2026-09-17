import { useState, useMemo, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Search, ChevronDown, ChevronUp, CheckCircle2, Play, Lock, BookOpen } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getSubjectsData, getSubjectWeeks } from '../../../services/student/studentService';
import styles from '../styles/Subjects.module.css';

// ⚠️ Redesigned for the real backend
// (StudentLearningProgressionController). Structural differences from the
// old mock version:
//
// - No "topics" layer — the backend goes straight from Subject → Weeks.
//   The old Subject → Topics → Weeks nesting doesn't exist here.
// - getSubjectsData() gives the subject list with a completedWeeks/totalWeeks
//   count; the per-week list (with unlocked/completed status) is fetched
//   separately per subject via getSubjectWeeks(subjectId) — lazily, only
//   when a subject is expanded, since fetching every subject's weeks
//   upfront isn't necessary.
// - No overall "topics done" pill or "current lesson" shortcut exists yet
//   (see studentService.js notes), so those were removed from the header.

export default function Subjects() {
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [subjects, setSubjects] = useState([]);

  const [activeYear, setActiveYear] = useState('All Years');
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedSubjectId, setExpandedSubjectId] = useState(null);
  const [weeksBySubject, setWeeksBySubject] = useState({}); // { [subjectId]: WeekAccessResponse[] }
  const [weeksLoading, setWeeksLoading] = useState(false);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      try {
        const data = await getSubjectsData();
        if (isMounted) setSubjects(data);
      } catch (err) {
        console.error(err);
        if (isMounted) setLoadError(true);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    loadData();
    return () => { isMounted = false; };
  }, []);

  const toggleSubject = useCallback(async (subjectId) => {
    if (expandedSubjectId === subjectId) {
      setExpandedSubjectId(null);
      return;
    }

    setExpandedSubjectId(subjectId);
    if (!weeksBySubject[subjectId]) {
      setWeeksLoading(true);
      try {
        const weeks = await getSubjectWeeks(subjectId);
        setWeeksBySubject((prev) => ({ ...prev, [subjectId]: weeks }));
      } catch (err) {
        console.error(err);
      } finally {
        setWeeksLoading(false);
      }
    }
  }, [expandedSubjectId, weeksBySubject]);

  const yearOptions = useMemo(() => {
    const years = Array.from(new Set(subjects.map((s) => s.yearLevel))).sort();
    return ['All Years', ...years];
  }, [subjects]);

  const filteredSubjects = useMemo(() => {
    return subjects
      .filter((s) => activeYear === 'All Years' || s.yearLevel === activeYear)
      .filter((s) => s.subjectName.toLowerCase().includes(searchQuery.toLowerCase()));
  }, [subjects, activeYear, searchQuery]);

  if (isLoading) {
    return (
      <PageShell navItems={STUDENT_NAV_ITEMS}>
        <p className={styles.loadingText}>Loading subjects...</p>
      </PageShell>
    );
  }

  if (loadError) {
    return (
      <PageShell navItems={STUDENT_NAV_ITEMS}>
        <p className={styles.loadingText}>Couldn't load subjects. Please refresh and try again.</p>
      </PageShell>
    );
  }

  return (
    <PageShell navItems={STUDENT_NAV_ITEMS}>
      <main className={styles.main}>
        <div className={styles.headerRow}>
          <div>
            <h1 className={styles.title}>Subjects</h1>
            <p className={styles.subtitle}>Select a subject to see its weekly lessons.</p>
          </div>

          <div className={styles.yearTabs}>
            {yearOptions.map((year) => (
              <button
                key={year}
                className={`${styles.yearTab} ${activeYear === year ? styles.yearTabActive : ''}`}
                onClick={() => setActiveYear(year)}
              >
                {year}
              </button>
            ))}
          </div>
        </div>

        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input
            type="text"
            className={styles.searchInput}
            placeholder="Search subjects"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div className={styles.subjectCard}>
          <div className={styles.topicsList}>
            {filteredSubjects.map((subject) => {
              const isExpanded = expandedSubjectId === subject.subjectId;
              const weeks = weeksBySubject[subject.subjectId];
              const isFullyCompleted = subject.completedWeeks === subject.totalWeeks;

              return (
                <div key={subject.subjectId} className={styles.topicWrapper}>
                  <button
                    className={`${styles.topicRow} ${isFullyCompleted ? styles.topicRowCompleted : ''} ${isExpanded ? styles.topicRowExpanded : ''}`}
                    onClick={() => toggleSubject(subject.subjectId)}
                  >
                    <span className={styles.topicIcon}>
                      {isFullyCompleted ? <CheckCircle2 size={18} /> : <BookOpen size={18} />}
                    </span>

                    <span className={styles.topicInfo}>
                      <span className={styles.topicTitle}>{subject.subjectName}</span>
                      <span className={styles.topicMeta}>
                        {subject.completedWeeks}/{subject.totalWeeks} weeks
                        {isFullyCompleted && <span className={styles.completedLabel}> · Completed</span>}
                      </span>
                    </span>

                    {isExpanded ? <ChevronUp size={18} className={styles.chevron} /> : <ChevronDown size={18} className={styles.chevron} />}
                  </button>

                  {isExpanded && (
                    <div className={styles.weeksPanel}>
                      {weeksLoading && !weeks && <p className={styles.loadingText}>Loading weeks...</p>}

                      {weeks?.map((week) => {
                        const status = week.completed ? 'done' : week.unlocked ? 'now' : 'locked';
                        const WeekIcon = status === 'done' ? CheckCircle2 : status === 'now' ? Play : Lock;

                        return (
                          <div
                            key={week.weekId}
                            role="button"
                            tabIndex={status === 'locked' ? -1 : 0}
                            className={`${styles.weekRow} ${styles[`weekRow_${status}`]} ${status === 'locked' ? styles.weekRowLocked : ''}`}
                            onClick={() => {
                              if (status !== 'locked') {
                                navigate(`/student/lesson/${subject.subjectId}/${week.weekId}`);
                              }
                            }}
                          >
                            <span className={`${styles.weekIcon} ${styles[`weekIcon_${status}`]}`}>
                              <WeekIcon size={16} />
                            </span>
                            <span className={styles.weekLabel}>
                              Week {week.weekNumber}: {week.title}
                            </span>
                            <span className={`${styles.weekBadge} ${styles[`weekBadge_${status}`]}`}>
                              {status === 'done' ? 'Done' : status === 'now' ? 'Available' : 'Locked'}
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>

        {filteredSubjects.length === 0 && (
          <p className={styles.emptyState}>No subjects match your search.</p>
        )}
      </main>
    </PageShell>
  );
}