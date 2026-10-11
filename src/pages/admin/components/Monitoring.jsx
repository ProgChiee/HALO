import { accountDisplay, accountTick } from '../../../utils/adminAccountDisplay';
import { apiErrorMessage } from '../../../utils/apiErrors';
import { useState, useMemo, useCallback } from 'react';
import { Activity, Search, Bot, MessageSquare, Users, TrendingUp, GraduationCap, AlertTriangle } from 'lucide-react';
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
import { useAuth } from '../../../context/login/useAuth';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { MONITORING_CATEGORIES } from '../../../data/admin/monitoringData';
import { getActivityLog, getProfessorMonitoring, getStudentMonitoring } from '../../../services/admin/adminService';
import { useToast } from '../../../context/notifications/useToast';
import { getActivityLogs, getAdminMonitoring } from '../../../services/superadmin/superadminService';
import { useRemoteData } from '../../../hooks/useRemoteData';
import styles from '../styles/Monitoring.module.css';

const TABS = [
  { id: 'activity', label: 'Activity Log' },
  { id: 'ai-usage', label: 'Professor Activity' },
  { id: 'quiz-performance', label: 'Student Performance' },
];

// recharts renders raw SVG, which doesn't reliably resolve CSS custom
// properties in all browsers — so the theme colors are duplicated here
// as literal hex values. Keep these in sync with src/styles/variables.css
// if the palette ever changes.
const CHART_COLORS = {
  bar: 'var(--color-success)',
  barMuted: 'var(--color-primary-border)',
  barMid: 'var(--color-warning)',
  barLow: 'var(--color-danger)',
  grid: 'var(--color-border)',
  textMuted: 'var(--color-text-muted)',
  tooltipBg: 'var(--color-surface)',
  tooltipBorder: 'var(--color-border)',
};

function UsageChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const item = payload[0].payload;

  return (
    <div className={styles.chartTooltip}>
      <p className={styles.chartTooltipTitle}>{accountDisplay(item.name)}</p>
      <p className={styles.chartTooltipRow}>{item.moduleActivities ?? 0} module activities</p>
    </div>
  );
}

function QuizChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const item = payload[0].payload;

  return (
    <div className={styles.chartTooltip}>
      <p className={styles.chartTooltipTitle}>{accountDisplay(item.name)}</p>
      <p className={styles.chartTooltipRow}>{item.latestAssessmentScore}% latest score</p>
    </div>
  );
}

export default function Monitoring() {
  const [activeTab, setActiveTab] = useState('activity');
  const { role } = useAuth();
  const isSuperAdmin = role === 'superadmin';
  const tabs = isSuperAdmin ? [TABS[0], { id: 'admin-activity', label: 'Admin Activity' }] : TABS;

  return (
    <PageShell
      responsive
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

      <div className={`${styles.tabRow} ${isSuperAdmin ? styles.superAdminTabs : ''}`}>
        {tabs.map((tab) => (
          <button
            aria-pressed={activeTab === tab.id}
            key={tab.id}
            className={`${styles.tabBtn} ${activeTab === tab.id ? styles.tabBtnActive : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'activity' && <ActivityLogTab loader={isSuperAdmin ? getActivityLogs : getActivityLog} paginated />}
      {!isSuperAdmin && activeTab === 'ai-usage' && <AiUsageTab />}
      {!isSuperAdmin && activeTab === 'quiz-performance' && <QuizPerformanceTab />}
      {activeTab === 'admin-activity' && isSuperAdmin && <AdminActivityTab />}
    </PageShell>
  );
}

// ---------------------------------------------------------------------------
// Tab 1: Activity Log
// ---------------------------------------------------------------------------
function ActivityLogTab({ loader, paginated = false }) {
  const [page, setPage] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeCategory, setActiveCategory] = useState('all');
  const serverSearch = paginated ? searchQuery : '';
  const serverCategory = paginated ? activeCategory : 'all';
  const requestKey = JSON.stringify([page, serverSearch, serverCategory]);
  const loadPage = useCallback(async (config) => {
    const result = await loader(paginated ? {
      ...config, params: { page, size: 20, search: serverSearch, ...(serverCategory === 'all' ? {} : { activityType: serverCategory }) },
    } : config);
    return paginated ? { ...result, requestKey } : result;
  }, [loader, paginated, page, serverSearch, serverCategory, requestKey]);
  const { data, isLoading, error: loadError, reload: loadLogs } = useRemoteData(loadPage, paginated ? { content: [], totalElements: 0, totalPages: 0 } : []);
  const logs = paginated ? data.content : data;
  const pagePending = paginated && (isLoading || data.requestKey !== requestKey);
  const { showToast } = useToast();

  async function handleRetry() {
    if (await loadLogs()) showToast('Activity log refreshed.', 'success');
  }

  const filteredLogs = useMemo(() => {
    return paginated ? logs : logs.filter((log) => {
      const matchesCategory = activeCategory === 'all' || log.activityType === activeCategory;
      const matchesSearch =
        (log.action ?? '').toLowerCase().includes(searchQuery.toLowerCase()) ||
        (log.userName ?? '').toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }, [logs, searchQuery, activeCategory, paginated]);

  // No severity/error concept exists on the real ActivityLogResponse (no
  // "type: success|error" field) — showing AUTH-category event count
  // instead of a fabricated "flagged events" number.
  const authEventCount = useMemo(
    () => logs.filter((log) => log.activityType === 'AUTH').length,
    [logs]
  );

  if (isLoading && (!paginated || !data.requestKey)) {
    return <div className={styles.main}><p className={styles.loadingText}>Loading activity log...</p></div>;
  }

  if (loadError && !paginated) {
    return (
      <div className={styles.main}>
        <div className={styles.errorState}>
          <p>{apiErrorMessage(loadError, "Couldn't load the activity log. Please try again.")}</p>
          <button className={styles.retryBtn} onClick={handleRetry}>Retry</button>
        </div>
      </div>
    );
  }

  return (
    <main className={`${styles.main} ${paginated ? styles.superAdminActivity : ''}`}>
      <div className={styles.summaryRow}>
        <div className={styles.summaryCard}>
          <p className={styles.summaryValue}>{paginated ? data.totalElements : logs.length}</p>
          <p className={styles.summaryLabel}>{paginated ? 'Matching events' : 'Total events'}</p>
        </div>
        <div className={styles.summaryCard}>
          <p className={styles.summaryValue}>{authEventCount}</p>
          <p className={styles.summaryLabel}>{paginated ? 'Auth events on this page' : 'Auth events'}</p>
        </div>
      </div>

      <div className={styles.toolbarRow}>
        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input
            type="text"
            className={styles.searchInput}
            aria-label="Search activity log"
            placeholder="Search activity log"
            maxLength={paginated ? 200 : undefined}
            value={searchQuery}
            onChange={(e) => { setSearchQuery(e.target.value); setPage(0); }}
          />
        </div>

        <div className={styles.filterRow}>
          {MONITORING_CATEGORIES.map((cat) => (
            <button
              aria-pressed={activeCategory === cat.id}
              key={cat.id}
              className={`${styles.filterChip} ${activeCategory === cat.id ? styles.filterChipActive : ''}`}
              onClick={() => { setActiveCategory(cat.id); setPage(0); }}
            >
              {cat.label}
            </button>
          ))}
        </div>
      </div>

      <div className={styles.logCard} aria-busy={pagePending && !loadError}>
        {paginated && loadError && <div className={styles.errorState}>
          <p>{apiErrorMessage(loadError, "Couldn't load the activity log. Please try again.")}</p>
          <button className={styles.retryBtn} onClick={handleRetry}>Retry</button>
        </div>}
        {pagePending && !loadError && <p role="status">Loading activity log...</p>}
        {!pagePending && !loadError && filteredLogs.map((log) => (
          <div key={log.id} className={styles.logRow}>
            <div className={styles.logContent}>
              <p className={styles.logText}>
                <span className={styles.activityActor}>{accountDisplay(log.userName)}</span> {log.action}
              </p>
              <p className={styles.logTime}>{new Date(log.createdAt).toLocaleString()}</p>
            </div>
            <span className={styles.logCategoryTag}>{log.activityType}</span>
          </div>
        ))}

        {!pagePending && !loadError && filteredLogs.length === 0 && (
          <p className={styles.emptyState}>No activity matches your search/filter.</p>
        )}
      </div>
      {paginated && <nav aria-label="Activity log pages" className={styles.toolbarRow}>
        <button className={styles.retryBtn} disabled={page === 0 || pagePending} onClick={() => setPage(p => p - 1)}>Previous</button>
        <span>Page {data.totalPages === 0 ? 0 : data.number + 1} of {data.totalPages}</span>
        <button className={styles.retryBtn} disabled={pagePending || page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
      </nav>}
    </main>
  );
}

// ---------------------------------------------------------------------------
// Tab 2: AI Usage
// ---------------------------------------------------------------------------
function AiUsageTab() {
  const [page, setPage] = useState(0);
  const loader = useCallback(async config => ({ ...await getProfessorMonitoring({ ...config, params: { page, size: 25 } }), requestPage: page }), [page]);
  const { data, isLoading: loading, error: loadError, reload: loadData } = useRemoteData(loader, { content: [], summary: {}, highlights: [] });
  const isLoading = loading || (!loadError && data.requestPage !== page);
  const professors = data.content;

  const sortedByActivity = useMemo(() => {
    return [...professors].sort((a, b) => (b.moduleActivities ?? 0) - (a.moduleActivities ?? 0));
  }, [professors]);

  const mostRecentlyActive = data.highlights.filter(p => p.lastActivity);

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

  const activeCount = data.summary.active ?? 0;
  const totalActivities = data.summary.moduleActivities ?? 0;
  const avgActivities = data.totalElements > 0 ? Math.round(totalActivities / data.totalElements) : 0;

  return (
    <main className={styles.main}>
      <div className={styles.statsGrid}>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><Users size={18} /></div>
          <p className={styles.statValue}>{data.totalElements}</p>
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
          <h2 className={styles.panelTitle}>Module Activities by Professor (ranked page)</h2>
          <div className={styles.chartScroll} role="region" aria-label="Monitoring chart, scroll horizontally" tabIndex={0}><div className={styles.chartCanvas}><ResponsiveContainer width="100%" height={Math.max(Math.min(sortedByActivity.length, 25) * 42, 200)}>
            <BarChart
              data={sortedByActivity.slice(0, 25)}
              layout="vertical"
              margin={{ top: 0, right: 24, bottom: 0, left: 0 }}
              barCategoryGap={10}
            >
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} horizontal={false} />
              <XAxis
                type="number"
                stroke={CHART_COLORS.textMuted}
                fontSize="var(--font-size-xs)"
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
              />
              <YAxis
                type="category"
                dataKey="name"
                width={160}
                stroke={CHART_COLORS.textMuted}
                fontSize="var(--font-size-xs)"
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={accountTick}
              />
              <Tooltip cursor={{ fill: 'var(--color-background)' }} content={<UsageChartTooltip />} />
              <Bar dataKey="moduleActivities" radius={[0, 4, 4, 0]} maxBarSize={18}>
                {sortedByActivity.slice(0, 25).map((entry, index) => (
                  <Cell
                    key={entry.userId}
                    fill={index === 0 ? CHART_COLORS.bar : CHART_COLORS.barMuted}
                  />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer></div></div>
        </div>

        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Recently Active Professors</h2>
          <div className={styles.topicList}>
            {mostRecentlyActive.map((prof) => (
              <div key={prof.userId} className={styles.topicRow}>
                <div className={styles.topicRank}>{prof.moduleActivities}</div>
                <div>
                  <p className={styles.topicText}>{accountDisplay(prof.name)}</p>
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
      {!data.content.length && <p className={styles.emptyState}>No monitoring records on this page.</p>}
      <nav aria-label="Monitoring pages" className={styles.toolbarRow}>
        <button className={styles.retryBtn} disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
        <span>Page {data.totalPages === 0 ? 0 : data.number + 1} of {data.totalPages}</span>
        <button className={styles.retryBtn} disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
      </nav>
    </main>
  );
}

// ---------------------------------------------------------------------------
// Tab 3: Quiz Performance
// ---------------------------------------------------------------------------
function QuizPerformanceTab() {
  const [page, setPage] = useState(0);
  const loader = useCallback(async config => ({ ...await getStudentMonitoring({ ...config, params: { page, size: 25 } }), requestPage: page }), [page]);
  const { data, isLoading: loading, error: loadError, reload: loadData } = useRemoteData(loader, { content: [], summary: {}, highlights: [] });
  const isLoading = loading || (!loadError && data.requestPage !== page);
  const students = data.content;

  // Only students with at least one scored assessment show up in the chart
  const scoredStudents = useMemo(
    () => students.filter((s) => s.latestAssessmentScore != null),
    [students]
  );

  const sortedByScore = useMemo(() => {
    return [...scoredStudents].sort((a, b) => b.latestAssessmentScore - a.latestAssessmentScore);
  }, [scoredStudents]);

  const lowestScoring = data.highlights;

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

  const avgScoreOverall = Math.round(data.summary.averageScore ?? 0);
  const totalPassedAssessments = data.summary.passedAssessments ?? 0;

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
          <p className={styles.statValue}>{data.summary.scored ?? 0}</p>
          <p className={styles.statLabel}>Students with a scored attempt</p>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statIcon}><MessageSquare size={18} /></div>
          <p className={styles.statValue}>{data.totalElements}</p>
          <p className={styles.statLabel}>Total students</p>
        </div>
      </div>

      <div className={styles.bottomGrid}>
        <div className={styles.panelCard}>
          <h2 className={styles.panelTitle}>Latest Score by Student (ranked page)</h2>
          <div className={styles.chartScroll} role="region" aria-label="Monitoring chart, scroll horizontally" tabIndex={0}><div className={styles.chartCanvas}><ResponsiveContainer width="100%" height={Math.max(Math.min(sortedByScore.length, 25) * 42, 200)}>
            <BarChart
              data={sortedByScore.slice(0, 25)}
              layout="vertical"
              margin={{ top: 0, right: 24, bottom: 0, left: 0 }}
              barCategoryGap={10}
            >
              <CartesianGrid strokeDasharray="3 3" stroke={CHART_COLORS.grid} horizontal={false} />
              <XAxis
                type="number"
                domain={[0, 100]}
                stroke={CHART_COLORS.textMuted}
                fontSize="var(--font-size-xs)"
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={(v) => `${v}%`}
              />
              <YAxis
                type="category"
                dataKey="name"
                width={160}
                stroke={CHART_COLORS.textMuted}
                fontSize="var(--font-size-xs)"
                tickLine={false}
                axisLine={{ stroke: CHART_COLORS.grid }}
                tickFormatter={accountTick}
              />
              <Tooltip cursor={{ fill: 'var(--color-background)' }} content={<QuizChartTooltip />} />
              <Bar dataKey="latestAssessmentScore" radius={[0, 4, 4, 0]} maxBarSize={18}>
                {sortedByScore.slice(0, 25).map((entry) => (
                  <Cell key={entry.userId} fill={barColorFor(entry.latestAssessmentScore)} />
                ))}
              </Bar>
            </BarChart>
          </ResponsiveContainer></div></div>
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
                  <p className={styles.topicText}>{accountDisplay(student.name)}</p>
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
      {!data.content.length && <p className={styles.emptyState}>No monitoring records on this page.</p>}
      <nav aria-label="Monitoring pages" className={styles.toolbarRow}>
        <button className={styles.retryBtn} disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button>
        <span>Page {data.totalPages === 0 ? 0 : data.number + 1} of {data.totalPages}</span>
        <button className={styles.retryBtn} disabled={page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next</button>
      </nav>
    </main>
  );
}

// ---------------------------------------------------------------------------
function AdminActivityTab() {
  const { data: admins, isLoading, error, reload } = useRemoteData(getAdminMonitoring, []);
  if (isLoading) return <p className={styles.loadingText}>Loading admin activity...</p>;
  if (error) return <div className={styles.errorState}>
    <p>{apiErrorMessage(error, "Couldn't load admin activity.")}</p>
    <button className={styles.retryBtn} onClick={reload}>Retry</button>
  </div>;
  return <main className={`${styles.main} ${styles.superAdminActivity}`}>
    <h2 className={styles.panelTitle}>Admin Account Activity</h2>
    <div className={styles.logCard}>
      {admins.map((admin) => <div key={admin.adminId} className={styles.logRow}>
        <div className={styles.logContent}>
          <p className={styles.logText}>{accountDisplay(admin.name)}: {admin.accountActivities ?? 0} account activities</p>
          <p className={styles.logTime}>{admin.lastActivity ? new Date(admin.lastActivity).toLocaleString() : 'No activity yet'}</p>
        </div>
        <span className={styles.logCategoryTag}>{admin.status}</span>
      </div>)}
      {!admins.length && <p className={styles.emptyState}>No admin activity yet.</p>}
    </div>
  </main>;
}
