import { useState, useEffect, useMemo } from 'react';
import { Activity, Search, Bot, MessageSquare, Users, TrendingUp, GraduationCap, AlertTriangle, LogIn, ShieldAlert } from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Cell,
} from 'recharts';
import PageShell from '../../../components/shared/PageShell';
import { useAuth } from '../../../context/login/AuthContext';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { MONITORING_CATEGORIES } from '../../../data/admin/monitoringData';
import { getActivityLog, getAiUsageData, getQuizPerformanceData, getSessionMonitoringData } from '../../../services/admin/adminService';
import { useToast } from '../../../context/notifications/ToastContext';
import styles from '../styles/Monitoring.module.css';

const TABS = [
  { id: 'activity', label: 'Activity Log' },
  { id: 'ai-usage', label: 'AI Usage' },
  { id: 'quiz-performance', label: 'Quiz Performance' },
  { id: 'login-sessions', label: 'Login Activity' },
];

// recharts renders raw SVG, which doesn't reliably resolve CSS custom
// properties in all browsers — so the theme colors are duplicated here
// as literal hex values. Keep these in sync with src/styles/variables.css
// if the palette ever changes.
const CHART_COLORS = {
  bar: '#00D45C',
  barMuted: 'rgba(0, 212, 92, 0.35)',
  barMid: '#E6B84A',
  barLow: '#e65a5a',
  grid: '#2F2F2F',
  textMuted: '#9A9494',
  tooltipBg: '#171B18',
  tooltipBorder: '#2F2F2F',
};

function UsageChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const item = payload[0].payload;

  return (
    <div className={styles.chartTooltip}>
      <p className={styles.chartTooltipTitle}>{item.subject}</p>
      <p className={styles.chartTooltipRow}>{item.queries} queries</p>
      <p className={styles.chartTooltipRow}>{item.activeStudents} active students</p>
    </div>
  );
}

function QuizChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const item = payload[0].payload;

  return (
    <div className={styles.chartTooltip}>
      <p className={styles.chartTooltipTitle}>{item.subject}</p>
      <p className={styles.chartTooltipRow}>{item.avgScore}% average score</p>
      <p className={styles.chartTooltipRow}>{item.attempts} attempts</p>
    </div>
  );
}

export default function Monitoring() {
  const [activeTab, setActiveTab] = useState('activity');
  const { role } = useAuth();
  const isSuperAdmin = role === 'superadmin';

  return (
    <PageShell
      navItems={isSuperAdmin ? SUPERADMIN_NAV_ITEMS : ADMIN_NAV_ITEMS}
      sectionLabel={isSuperAdmin ? 'Superadmin' : 'Admin'}
      roleBadge={isSuperAdmin ? 'Superadmin' : 'Admin'}
    >
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <Activity size={16} />
          Monitoring
          <span className={styles.breadcrumbSub}>System &amp; AI Activity Overview</span>
        </div>
      </header>

      <div className={styles.tabRow}>
        {TABS.map((tab) => (
          <button
            key={tab.id}
            className={`${styles.tabBtn} ${activeTab === tab.id ? styles.tabBtnActive : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'activity' && <ActivityLogTab />}
      {activeTab === 'ai-usage' && <AiUsageTab />}
      {activeTab === 'quiz-performance' && <QuizPerformanceTab />}
      {activeTab === 'login-sessions' && <LoginSessionsTab />}
    </PageShell>
  );
}

// ---------------------------------------------------------------------------
// Tab 1: Activity Log
// ---------------------------------------------------------------------------
function ActivityLogTab() {
  const [isLoading, setIsLoading] = useState(true);
  const [logs, setLogs] = useState([]);
  const [loadError, setLoadError] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState('all');
  const { showToast } = useToast();

  useEffect(() => {
    loadLogs();
  }, []);

  async function loadLogs() {
    setIsLoading(true);
    setLoadError(false);
    try {
      const data = await getActivityLog();
      setLogs(data);
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  async function handleRetry() {
    await loadLogs();
    if (!loadError) {
      showToast('Activity log refreshed.', 'success');
    }
  }

  const filteredLogs = useMemo(() => {
    return logs.filter((log) => {
      const matchesCategory = activeCategory === 'all' || log.category === activeCategory;
      const matchesSearch = log.text.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }, [logs, searchQuery, activeCategory]);

  const errorCount = useMemo(
    () => logs.filter((log) => log.type === 'error').length,
    [logs]
  );

  if (isLoading) {
    return <div className={styles.main}><p className={styles.loadingText}>Loading activity log...</p></div>;
  }

  if (loadError) {
    return (
      <div className={styles.main}>
        <div className={styles.errorState}>
          <p>Couldn't load the activity log. Please check your connection and try again.</p>
          <button className={styles.retryBtn} onClick={handleRetry}>Retry</button>
        </div>
      </div>
    );
  }

  return (
    <main className={styles.main}>
      <div className={styles.summaryRow}>
        <div className={styles.summaryCard}>
          <p className={styles.summaryValue}>{logs.length}</p>
          <p className={styles.summaryLabel}>Total events</p>
        </div>
        <div className={`${styles.summaryCard} ${styles.summaryCardAlert}`}>
          <p className={styles.summaryValue}>{errorCount}</p>
          <p className={styles.summaryLabel}>Flagged events</p>
        </div>
      </div>

      <div className={styles.toolbarRow}>
        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input
            type="text"
            className={styles.searchInput}
            placeholder="Search activity log"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        <div className={styles.filterRow}>
          {MONITORING_CATEGORIES.map((cat) => (
            <button
              key={cat.id}
              className={`${styles.filterChip} ${activeCategory === cat.id ? styles.filterChipActive : ''}`}
              onClick={() => setActiveCategory(cat.id)}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      <div className={styles.logCard}>
        {filteredLogs.map((log) => (
          <div key={log.id} className={styles.logRow}>
            <span className={`${styles.logDot} ${styles[`logDot_${log.type}`]}`} />
            <div className={styles.logContent}>
              <p className={styles.logText}>{log.text}</p>
              <p className={styles.logTime}>{log.time}</p>
            </div>
            <span className={styles.logCategoryTag}>{log.category}</span>
          </div>
        ))}

        {filteredLogs.length === 0 && (
          <p className={styles.emptyState}>No activity matches your search/filter.</p>
        )}
      </div>
    </main>
  );
}

// ---------------------------------------------------------------------------
// Tab 2: AI Usage
// ---------------------------------------------------------------------------
function AiUsageTab() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [data, setData] = useState(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setIsLoading(true);
    setLoadError(false);
    try {
      const result = await getAiUsageData();
      setData(result);
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  const sortedSubjects = useMemo(() => {
    if (!data) return [];
    return [...data.bySubject].sort((a, b) => b.queries - a.queries);
  }, [data]);

  if (isLoading) {
    return <div className={styles.main}><p className={styles.loadingText}>Loading AI usage data...</p></div>;
  }

  if (loadError || !data) {
    return (
      <div className={styles.main}>
        <div className={styles.errorState}>
          <p>Couldn't load AI usage data. Please check your connection and try again.</p>
          <button className={styles.retryBtn} onClick={loadData}>Retry</button>
        </div>
      </div>
    );
  }

  const { summary, topTopics } = data;

  return (
    <main className={styles.main}>
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><MessageSquare size={18} /></div>
          <p className={styles.statValue}>{summary.totalQueriesToday}</p>
          <p className={styles.statLabel}>Queries today</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><TrendingUp size={18} /></div>
          <p className={styles.statValue}>{summary.totalQueriesThisWeek}</p>
          <p className={styles.statLabel}>Queries this week</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Users size={18} /></div>
          <p className={styles.statValue}>{summary.activeStudentsToday}</p>
          <p className={styles.statLabel}>Active students today</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Bot size={18} /></div>
          <p className={styles.statValue}>{summary.avgQueriesPerStudent}</p>
          <p className={styles.statLabel}>Avg. queries / student</p>
        </div>
      </div>

      <div className={styles.bottomGrid}>
        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Usage by Subject</h2>
          <ResponsiveContainer width="100%" height={Math.max(sortedSubjects.length * 42, 200)}>
            <BarChart
              data={sortedSubjects}
              layout="vertical"
              margin={{ top: 0, right: 24, bottom: 0, left: 0 }}
              barCategoryGap={10}
            >
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} horizontal={false} />
              <XAxis
                type="number"
                stroke={CHART_COLORS.textMuted}
                fontSize={11}
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
              />
              <YAxis
                type="category"
                dataKey="subject"
                width={160}
                stroke={CHART_COLORS.textMuted}
                fontSize={11}
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={(name) => (name.length > 22 ? `${name.slice(0, 22)}…` : name)}
              />
              <Tooltip cursor={{ fill: 'rgba(255,255,255,0.04)' }} content={<UsageChartTooltip />} />
              <Bar dataKey="queries" radius={[0, 4, 4, 0]} maxBarSize={18}>
                {sortedSubjects.map((entry, index) => (
                  <Cell
                    key={entry.id}
                    fill={index === 0 ? CHART_COLORS.bar : CHART_COLORS.barMuted}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Most Asked Topics</h2>
          <div className={styles.topicList}>
            {topTopics.map((item) => (
              <div key={item.id} className={styles.topicRow}>
                <div className={styles.topicRank}>{item.count}</div>
                <div>
                  <p className={styles.topicText}>{item.topic}</p>
                  <p className={styles.topicSubject}>{item.subject}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}

// ---------------------------------------------------------------------------
// Tab 3: Quiz Performance
// ---------------------------------------------------------------------------
function QuizPerformanceTab() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [data, setData] = useState(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setIsLoading(true);
    setLoadError(false);
    try {
      const result = await getQuizPerformanceData();
      setData(result);
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  const sortedSubjects = useMemo(() => {
    if (!data) return [];
    return [...data.bySubject].sort((a, b) => b.avgScore - a.avgScore);
  }, [data]);

  if (isLoading) {
    return <div className={styles.main}><p className={styles.loadingText}>Loading quiz performance data...</p></div>;
  }

  if (loadError || !data) {
    return (
      <div className={styles.main}>
        <div className={styles.errorState}>
          <p>Couldn't load quiz performance data. Please check your connection and try again.</p>
          <button className={styles.retryBtn} onClick={loadData}>Retry</button>
        </div>
      </div>
    );
  }

  const { summary, lowestScoring } = data;

  // Color-code bars by score band so low-performing subjects stand out
  // at a glance, not just on hover.
  function barColorFor(score) {
    if (score < 60) return CHART_COLORS.barLow;
    if (score < 75) return CHART_COLORS.barMid;
    return CHART_COLORS.bar;
  }

  return (
    <main className={styles.main}>
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><GraduationCap size={18} /></div>
          <p className={styles.statValue}>{summary.avgScoreOverall}%</p>
          <p className={styles.statLabel}>Average score</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><TrendingUp size={18} /></div>
          <p className={styles.statValue}>{summary.passRateOverall}%</p>
          <p className={styles.statLabel}>Pass rate</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Users size={18} /></div>
          <p className={styles.statValue}>{summary.totalAttemptsToday}</p>
          <p className={styles.statLabel}>Attempts today</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><MessageSquare size={18} /></div>
          <p className={styles.statValue}>{summary.totalQuizzesTaken}</p>
          <p className={styles.statLabel}>Total quizzes taken</p>
        </div>
      </div>

      <div className={styles.bottomGrid}>
        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Average Score by Subject</h2>
          <ResponsiveContainer width="100%" height={Math.max(sortedSubjects.length * 42, 200)}>
            <BarChart
              data={sortedSubjects}
              layout="vertical"
              margin={{ top: 0, right: 24, bottom: 0, left: 0 }}
              barCategoryGap={10}
            >
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} horizontal={false} />
              <XAxis
                type="number"
                domain={[0, 100]}
                stroke={CHART_COLORS.textMuted}
                fontSize={11}
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={(v) => `${v}%`}
              />
              <YAxis
                type="category"
                dataKey="subject"
                width={160}
                stroke={CHART_COLORS.textMuted}
                fontSize={11}
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={(name) => (name.length > 22 ? `${name.slice(0, 22)}…` : name)}
              />
              <Tooltip cursor={{ fill: 'rgba(255,255,255,0.04)' }} content={<QuizChartTooltip />} />
              <Bar dataKey="avgScore" radius={[0, 4, 4, 0]} maxBarSize={18}>
                {sortedSubjects.map((entry) => (
                  <Cell key={entry.id} fill={barColorFor(entry.avgScore)} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
          <div className={styles.legendRow}>
            <span className={styles.legendItem}><span className={`${styles.legendDot} ${styles.legendDotGood}`} /> 75%+</span>
            <span className={styles.legendItem}><span className={`${styles.legendDot} ${styles.legendDotMid}`} /> 60–74%</span>
            <span className={styles.legendItem}><span className={`${styles.legendDot} ${styles.legendDotLow}`} /> Below 60%</span>
          </div>
        </div>

        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Lowest-Scoring Quizzes</h2>
          <p className={styles.panelSubtitle}>May need content review by the subject professor</p>
          <div className={styles.topicList}>
            {lowestScoring.map((item) => (
              <div key={item.id} className={styles.topicRow}>
                <div className={`${styles.topicRank} ${styles.topicRankAlert}`}>
                  <AlertTriangle size={14} />
                </div>
                <div>
                  <p className={styles.topicText}>{item.quizTitle}</p>
                  <p className={styles.topicSubject}>
                    {item.subject} · {item.avgScore}% avg · {item.attempts} attempts
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}

// ---------------------------------------------------------------------------
// Tab 4: Login / Session Monitoring
// ---------------------------------------------------------------------------
function LoginSessionsTab() {
  const [isLoading, setIsLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [data, setData] = useState(null);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setIsLoading(true);
    setLoadError(false);
    try {
      const result = await getSessionMonitoringData();
      setData(result);
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  if (isLoading) {
    return <div className={styles.main}><p className={styles.loadingText}>Loading login activity...</p></div>;
  }

  if (loadError || !data) {
    return (
      <div className={styles.main}>
        <div className={styles.errorState}>
          <p>Couldn't load login activity. Please check your connection and try again.</p>
          <button className={styles.retryBtn} onClick={loadData}>Retry</button>
        </div>
      </div>
    );
  }

  const { summary, byRole, activity } = data;
  const maxRoleCount = Math.max(...byRole.map((r) => r.count), 1);

  return (
    <main className={styles.main}>
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><LogIn size={18} /></div>
          <p className={styles.statValue}>{summary.activeSessionsNow}</p>
          <p className={styles.statLabel}>Active sessions now</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Users size={18} /></div>
          <p className={styles.statValue}>{summary.loginsToday}</p>
          <p className={styles.statLabel}>Logins today</p>
        </div>
        <div className={`${styles.statCard} ${summary.failedAttemptsToday > 0 ? styles.statCardAlert : ''}`}>
          <div className={styles.statIcon}><ShieldAlert size={18} /></div>
          <p className={styles.statValue}>{summary.failedAttemptsToday}</p>
          <p className={styles.statLabel}>Failed attempts today</p>
        </div>
      </div>

      <div className={styles.bottomGrid}>
        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Active Sessions by Role</h2>
          <div className={styles.usageList}>
            {byRole.map((r) => (
              <div key={r.role} className={styles.usageRow}>
                <div className={styles.usageHeader}>
                  <span className={styles.usageName}>{r.role}</span>
                  <span className={styles.usageCount}>{r.count}</span>
                </div>
                <div className={styles.usageBarTrack}>
                  <div
                    className={styles.usageBarFill}
                    style={{ width: `${(r.count / maxRoleCount) * 100}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Recent Login Activity</h2>
          <div className={styles.logCard}>
            {activity.map((entry) => (
              <div key={entry.id} className={styles.logRow}>
                <span className={`${styles.logDot} ${styles[`logDot_${entry.status}`]}`} />
                <div className={styles.logContent}>
                  <p className={styles.logText}>
                    {entry.name}
                    {entry.status === 'failed' ? ' — failed login attempt' : ' logged in'}
                  </p>
                  <p className={styles.logTime}>{entry.time}</p>
                </div>
                {entry.role !== '—' && <span className={styles.logCategoryTag}>{entry.role}</span>}
              </div>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}