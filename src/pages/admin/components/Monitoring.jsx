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
import { getActivityLog, getProfessorMonitoring, getStudentMonitoring, getSessionMonitoringData } from '../../../services/admin/adminService';
import { useToast } from '../../../context/notifications/ToastContext';
import styles from '../styles/Monitoring.module.css';

const TABS = [
  { id: 'activity', label: 'Activity Log' },
  { id: 'ai-usage', label: 'Professor Activity' },
  { id: 'quiz-performance', label: 'Student Performance' },
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
      const matchesCategory = activeCategory === 'all' || log.activityType === activeCategory;
      const matchesSearch =
        log.action.toLowerCase().includes(searchQuery.toLowerCase()) ||
        log.userName.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }, [logs, searchQuery, activeCategory]);

  // No severity/error concept exists on the real ActivityLogResponse (no
  // "type: success|error" field) — showing AUTH-category event count
  // instead of a fabricated "flagged events" number.
  const authEventCount = useMemo(
    () => logs.filter((log) => log.activityType === 'AUTH').length,
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
        <div className={styles.summaryCard}>
          <p className={styles.summaryValue}>{authEventCount}</p>
          <p className={styles.summaryLabel}>Auth events</p>
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
            <div className={styles.logContent}>
              <p className={styles.logText}>
                <span className={styles.activityActor}>{log.userName}</span> {log.action}
              </p>
              <p className={styles.logTime}>{new Date(log.createdAt).toLocaleString()}</p>
            </div>
            <span className={styles.logCategoryTag}>{log.activityType}</span>
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
  const [professors, setProfessors] = useState([]);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setIsLoading(true);
    setLoadError(false);
    try {
      const result = await getProfessorMonitoring();
      setProfessors(result);
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  const sortedByActivity = useMemo(() => {
    return [...professors].sort((a, b) => (b.moduleActivities ?? 0) - (a.moduleActivities ?? 0));
  }, [professors]);

  const mostRecentlyActive = useMemo(() => {
    return [...professors]
      .filter((p) => p.lastActivity)
      .sort((a, b) => new Date(b.lastActivity) - new Date(a.lastActivity))
      .slice(0, 8);
  }, [professors]);

  if (isLoading) {
    return <div className={styles.main}><p className={styles.loadingText}>Loading professor activity...</p></div>;
  }

  if (loadError) {
    return (
      <div className={styles.main}>
        <div className={styles.errorState}>
          <p>Couldn't load professor activity. Please check your connection and try again.</p>
          <button className={styles.retryBtn} onClick={loadData}>Retry</button>
        </div>
      </div>
    );
  }

  const activeCount = professors.filter((p) => p.status === 'ACTIVE').length;
  const totalActivities = professors.reduce((sum, p) => sum + (p.moduleActivities ?? 0), 0);
  const avgActivities = professors.length > 0 ? Math.round(totalActivities / professors.length) : 0;

  return (
    <main className={styles.main}>
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Users size={18} /></div>
          <p className={styles.statValue}>{professors.length}</p>
          <p className={styles.statLabel}>Total professors</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Bot size={18} /></div>
          <p className={styles.statValue}>{activeCount}</p>
          <p className={styles.statLabel}>Active professors</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><MessageSquare size={18} /></div>
          <p className={styles.statValue}>{totalActivities}</p>
          <p className={styles.statLabel}>Total module activities</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><TrendingUp size={18} /></div>
          <p className={styles.statValue}>{avgActivities}</p>
          <p className={styles.statLabel}>Avg. activities / professor</p>
        </div>
      </div>

      <div className={styles.bottomGrid}>
        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Module Activities by Professor</h2>
          <ResponsiveContainer width="100%" height={Math.max(sortedByActivity.length * 42, 200)}>
            <BarChart
              data={sortedByActivity}
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
                dataKey="name"
                width={160}
                stroke={CHART_COLORS.textMuted}
                fontSize={11}
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={(name) => (name.length > 22 ? `${name.slice(0, 22)}…` : name)}
              />
              <Tooltip cursor={{ fill: 'rgba(255,255,255,0.04)' }} content={<UsageChartTooltip />} />
              <Bar dataKey="moduleActivities" radius={[0, 4, 4, 0]} maxBarSize={18}>
                {sortedByActivity.map((entry, index) => (
                  <Cell
                    key={entry.userId}
                    fill={index === 0 ? CHART_COLORS.bar : CHART_COLORS.barMuted}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Recently Active Professors</h2>
          <div className={styles.topicList}>
            {mostRecentlyActive.map((prof) => (
              <div key={prof.userId} className={styles.topicRow}>
                <div className={styles.topicRank}>{prof.moduleActivities}</div>
                <div>
                  <p className={styles.topicText}>{prof.name}</p>
                  <p className={styles.topicSubject}>{new Date(prof.lastActivity).toLocaleString()}</p>
                </div>
              </div>
            ))}
            {mostRecentlyActive.length === 0 && (
              <p className={styles.loadingText}>No recent professor activity.</p>
            )}
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
  const [students, setStudents] = useState([]);

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    setIsLoading(true);
    setLoadError(false);
    try {
      const result = await getStudentMonitoring();
      setStudents(result);
    } catch (err) {
      console.error(err);
      setLoadError(true);
    } finally {
      setIsLoading(false);
    }
  }

  // Only students with at least one scored assessment show up in the chart
  const scoredStudents = useMemo(
    () => students.filter((s) => s.latestAssessmentScore != null),
    [students]
  );

  const sortedByScore = useMemo(() => {
    return [...scoredStudents].sort((a, b) => b.latestAssessmentScore - a.latestAssessmentScore);
  }, [scoredStudents]);

  const lowestScoring = useMemo(() => {
    return [...scoredStudents].sort((a, b) => a.latestAssessmentScore - b.latestAssessmentScore).slice(0, 8);
  }, [scoredStudents]);

  if (isLoading) {
    return <div className={styles.main}><p className={styles.loadingText}>Loading quiz performance data...</p></div>;
  }

  if (loadError) {
    return (
      <div className={styles.main}>
        <div className={styles.errorState}>
          <p>Couldn't load quiz performance data. Please check your connection and try again.</p>
          <button className={styles.retryBtn} onClick={loadData}>Retry</button>
        </div>
      </div>
    );
  }

  function barColorFor(score) {
    if (score < 60) return CHART_COLORS.barLow;
    if (score < 75) return CHART_COLORS.barMid;
    return CHART_COLORS.bar;
  }

  const avgScoreOverall = scoredStudents.length > 0
    ? Math.round(scoredStudents.reduce((sum, s) => sum + s.latestAssessmentScore, 0) / scoredStudents.length)
    : 0;
  const totalPassedAssessments = students.reduce((sum, s) => sum + (s.passedAssessments ?? 0), 0);

  return (
    <main className={styles.main}>
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><GraduationCap size={18} /></div>
          <p className={styles.statValue}>{avgScoreOverall}%</p>
          <p className={styles.statLabel}>Average latest score</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><TrendingUp size={18} /></div>
          <p className={styles.statValue}>{totalPassedAssessments}</p>
          <p className={styles.statLabel}>Total passed assessments</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Users size={18} /></div>
          <p className={styles.statValue}>{scoredStudents.length}</p>
          <p className={styles.statLabel}>Students with a scored attempt</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><MessageSquare size={18} /></div>
          <p className={styles.statValue}>{students.length}</p>
          <p className={styles.statLabel}>Total students</p>
        </div>
      </div>

      <div className={styles.bottomGrid}>
        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Latest Score by Student</h2>
          <ResponsiveContainer width="100%" height={Math.max(sortedByScore.length * 42, 200)}>
            <BarChart
              data={sortedByScore}
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
                dataKey="name"
                width={160}
                stroke={CHART_COLORS.textMuted}
                fontSize={11}
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={(name) => (name.length > 22 ? `${name.slice(0, 22)}…` : name)}
              />
              <Tooltip cursor={{ fill: 'rgba(255,255,255,0.04)' }} content={<QuizChartTooltip />} />
              <Bar dataKey="latestAssessmentScore" radius={[0, 4, 4, 0]} maxBarSize={18}>
                {sortedByScore.map((entry) => (
                  <Cell key={entry.userId} fill={barColorFor(entry.latestAssessmentScore)} />
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
          <h2 className={styles.panelTitle}>Lowest-Scoring Students</h2>
          <p className={styles.panelSubtitle}>May need extra support from their professor</p>
          <div className={styles.topicList}>
            {lowestScoring.map((student) => (
              <div key={student.userId} className={styles.topicRow}>
                <div className={`${styles.topicRank} ${styles.topicRankAlert}`}>
                  <AlertTriangle size={14} />
                </div>
                <div>
                  <p className={styles.topicText}>{student.name}</p>
                  <p className={styles.topicSubject}>
                    {student.section} · {student.latestAssessmentScore}% latest score
                  </p>
                </div>
              </div>
            ))}
            {lowestScoring.length === 0 && (
              <p className={styles.loadingText}>No scored assessments yet.</p>
            )}
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