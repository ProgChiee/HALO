import { useState, useEffect } from 'react';
import { CheckCircle2, BookOpen, Star, Award } from 'lucide-react';
import { Link } from 'react-router-dom';
import Sidebar from '../../../components/shared/Sidebar';
import { STUDENT_NAV_ITEMS } from '../../../data/navigationData';
import { mockModuleProgress } from '../../../data/student/studentDashboardData';
import { getProgressData } from '../../../services/student/studentService';
import styles from '../styles/Progress.module.css';

// Maps the string icon names from the mock data to actual icon components
const ICONS = { BookOpen, CheckCircle2, Star, Award };

// Simple color coding for letter grades — tweak as needed
function gradeClass(grade) {
  if (grade.startsWith('A')) return styles.gradeA;
  if (grade.startsWith('B')) return styles.gradeB;
  return styles.gradeC;
}

export default function Progress() {
  const [isLoading, setIsLoading] = useState(true);
  const [progressData, setProgressData] = useState(null);

  useEffect(() => {
    let isMounted = true;

    async function loadData() {
      const data = await getProgressData();
      if (isMounted) {
        setProgressData(data);
        setIsLoading(false);
      }
    }

    loadData();
    return () => { isMounted = false; };
  }, []);

  if (isLoading || !progressData) {
    return (
      <div className={styles.layout}>
        <Sidebar navItems={STUDENT_NAV_ITEMS} progress={null} />
        <div className={styles.contentArea}>
          <p className={styles.loadingText}>Loading your progress...</p>
        </div>
      </div>
    );
  }

  const { overall, stats, completedSubjects, quizScores } = progressData;

  return (
    <div className={styles.layout}>
      <Sidebar navItems={STUDENT_NAV_ITEMS} progress={mockModuleProgress} />

      <div className={styles.contentArea}>
        <header className={styles.topbar}>
          <div className={styles.breadcrumb}>
            <CheckCircle2 size={16} />
            Progress
          </div>
        </header>

        <main className={styles.main}>
          <div className={styles.overviewRow}>
            <div className={styles.overallBlock}>
              <div className={styles.progressRing}>
                <svg width="140" height="140" viewBox="0 0 140 140">
                  <circle
                    cx="70"
                    cy="70"
                    r="60"
                    fill="none"
                    stroke="rgba(255, 255, 255, 0.08)"
                    strokeWidth="10"
                  />
                  <circle
                    cx="70"
                    cy="70"
                    r="60"
                    fill="none"
                    stroke="var(--color-accent-active)"
                    strokeWidth="10"
                    strokeLinecap="round"
                    strokeDasharray={2 * Math.PI * 60}
                    strokeDashoffset={2 * Math.PI * 60 * (1 - overall / 100)}
                    transform="rotate(-90 70 70)"
                    className={styles.progressRingFill}
                  />
                </svg>
                <div className={styles.progressRingText}>
                  <p className={styles.overallPercent}>{overall}%</p>
                </div>
              </div>
              <p className={styles.overallLabel}>Overall</p>
            </div>

            <div className={styles.statsGrid}>
              {stats.map((stat) => {
                const Icon = ICONS[stat.icon];
                return (
                  <div key={stat.id} className={styles.statCard}>
                    <span className={styles.statIcon}>
                      <Icon size={16} />
                    </span>
                    <div>
                      <p className={styles.statValue}>{stat.value}</p>
                      <p className={styles.statLabel}>{stat.label}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className={styles.sectionHeaderRow}>
            <h2 className={styles.sectionTitle}>Completed Subjects</h2>
            <Link to="/student/subjects" className={styles.viewAllLink}>View all →</Link>
          </div>
          <div className={styles.rowsList}>
            {completedSubjects.map((subject) => (
              <div key={subject.id} className={styles.subjectRow}>
                <span className={styles.subjectName}>{subject.name}</span>
                <span className={styles.subjectProgress}>{subject.progress}%</span>
              </div>
            ))}
          </div>

          <h2 className={styles.sectionTitle}>Quiz Scores</h2>
          <div className={styles.rowsList}>
            {quizScores.map((quiz) => (
              <div key={quiz.id} className={styles.quizRow}>
                <span className={styles.quizTitle}>{quiz.title}</span>
                <span className={styles.quizScoreGroup}>
                  <span className={styles.quizScore}>{quiz.score}%</span>
                  <span className={`${styles.gradeBadge} ${gradeClass(quiz.grade)}`}>
                    {quiz.grade}
                  </span>
                </span>
              </div>
            ))}
          </div>
        </main>
      </div>
    </div>
  );
}