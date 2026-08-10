import { useState, useMemo, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search,
  ChevronDown,
  ChevronUp,
  CheckCircle2,
  Play,
  Lock,
  Clock,
  Calendar,
  BookOpen,
} from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { getSubjectsData, getCurrentTopicAndWeek } from '../../../services/student/studentService';
import { mockModuleProgress } from '../../../data/student/studentDashboardData';
import styles from '../styles/Subjects.module.css';

const WEEK_STATUS_ICON = { done: CheckCircle2, now: Play, locked: Lock };

export default function Subjects() {
  const navigate = useNavigate();

  const [isLoading, setIsLoading] = useState(true);
  const [subjectsData, setSubjectsData] = useState(null);
  const [currentLesson, setCurrentLesson] = useState(null);

  const [activeYear, setActiveYear] = useState('All Years');
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedTopicId, setExpandedTopicId] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      const [data, lesson] = await Promise.all([
        getSubjectsData(),
        getCurrentTopicAndWeek(),
      ]);
      if (isMounted) {
        setSubjectsData(data);
        setCurrentLesson(lesson);
        setIsLoading(false);
      }
    }

    loadData();
    return () => { isMounted = false; };
  }, []);

  function toggleTopic(topicId) {
    setExpandedTopicId((prev) => (prev === topicId ? null : topicId));
  }

  function goToQuiz() {
    const { topic, week } = currentLesson || {};
    if (topic && week) navigate(`/student/quiz/${topic.id}/${week.id}`);
  }

  const filteredGroups = useMemo(() => {
    if (!subjectsData) return [];
    return subjectsData.subjectGroups
      .filter((group) => activeYear === 'All Years' || group.year === activeYear)
      .map((group) => ({
        ...group,
        topics: group.topics.filter((topic) =>
          topic.title.toLowerCase().includes(searchQuery.toLowerCase())
        ),
      }))
      .filter((group) => group.topics.length > 0);
  }, [subjectsData, activeYear, searchQuery]);

  if (isLoading || !subjectsData) {
    return (
      <PageShell navItems={STUDENT_NAV_ITEMS}>
        <p className={styles.loadingText}>Loading subjects...</p>
      </PageShell>
    );
  }

  const { yearFilters, overview } = subjectsData;

  return (
    <PageShell navItems={STUDENT_NAV_ITEMS} progress={mockModuleProgress}>
      <header className={styles.topbar}>
        <span className={styles.overviewPill}>
          {overview.topicsDone} of {overview.topicsTotal} topics done
        </span>
        <Button onClick={goToQuiz}>Start quiz</Button>
      </header>

      <main className={styles.main}>
        <div className={styles.headerRow}>
          <div>
            <h1 className={styles.title}>Subjects</h1>
            <p className={styles.subtitle}>Select a topic to begin your lesson with your AI Mentor.</p>
          </div>

          <div className={styles.yearTabs}>
            {yearFilters.map((year) => (
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
            placeholder="Search topics"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        {filteredGroups.map((group) => (
          <section key={group.id} className={styles.yearSection}>
            <div className={styles.yearSectionHeader}>
              <span className={styles.yearLabel}>{group.year}</span>
              <div className={styles.yearLine} />
            </div>

            <div className={styles.subjectCard}>
              <div className={styles.subjectCardHeader}>
                <h2 className={styles.subjectName}>{group.subjectName}</h2>
                <span className={styles.topicsCountPill}>{group.topics.length} topics</span>
              </div>

              <div className={styles.topicsList}>
                {group.topics.map((topic) => {
                  const isExpanded = expandedTopicId === topic.id;
                  const isCompleted = topic.status === 'completed';
                  const isLocked = topic.status === 'locked';

                  return (
                    <div key={topic.id} className={styles.topicWrapper}>
                      <button
                        className={`${styles.topicRow} ${isCompleted ? styles.topicRowCompleted : ''} ${isExpanded ? styles.topicRowExpanded : ''}`}
                        onClick={() => !isLocked && toggleTopic(topic.id)}
                        disabled={isLocked}
                      >
                        <span className={styles.topicIcon}>
                          {isCompleted ? (
                            <CheckCircle2 size={18} />
                          ) : isLocked ? (
                            <Lock size={18} />
                          ) : (
                            <BookOpen size={18} />
                          )}
                        </span>

                        <span className={styles.topicInfo}>
                          <span className={styles.topicTitle}>{topic.title}</span>
                          <span className={styles.topicMeta}>
                            {topic.lessonsCount} lessons
                            {isCompleted && <span className={styles.completedLabel}> · Completed</span>}
                          </span>
                        </span>

                        {!isLocked && (
                          isExpanded
                            ? <ChevronUp size={18} className={styles.chevron} />
                            : <ChevronDown size={18} className={styles.chevron} />
                        )}
                      </button>

                      {isExpanded && (
                        <div className={styles.weeksPanel}>
                          <div className={styles.weeksPanelHeader}>
                            <span className={styles.weeksPanelLabel}>
                              <Calendar size={14} />
                              WEEKLY MODULES
                            </span>
                            <span className={styles.currentWeekLabel}>
                              Currently on week {topic.currentWeek}
                            </span>
                          </div>

                          {topic.weeks.map((week) => {
                            const WeekIcon = WEEK_STATUS_ICON[week.status];
                            const isLockedWeek = week.status === 'locked';

                            return (
                              <div
                                key={week.id}
                                role="button"
                                tabIndex={isLockedWeek ? -1 : 0}
                                className={`${styles.weekRow} ${styles[`weekRow_${week.status}`]} ${isLockedWeek ? styles.weekRowLocked : ''}`}
                                onClick={() => {
                                  if (!isLockedWeek) {
                                    navigate(`/student/lesson/${topic.id}/${week.id}`);
                                  }
                                }}
                              >
                                <span className={`${styles.weekIcon} ${styles[`weekIcon_${week.status}`]}`}>
                                  <WeekIcon size={16} />
                                </span>
                                <span className={styles.weekLabel}>{week.label}</span>
                                <span className={styles.weekDuration}>
                                  <Clock size={14} />
                                  {week.duration}
                                </span>
                                <span className={`${styles.weekBadge} ${styles[`weekBadge_${week.status}`]}`}>
                                  {week.status === 'done' ? 'Done' : week.status === 'now' ? 'Now' : 'Locked'}
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
          </section>
        ))}

        {filteredGroups.length === 0 && (
          <p className={styles.emptyState}>No topics match your search.</p>
        )}
      </main>
    </PageShell>
  );
}