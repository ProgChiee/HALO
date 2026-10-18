import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { LayoutGrid, Users, BookOpen, FileText, CheckCircle2, ClipboardCheck, Award, ArrowRight, Clock3, CircleAlert } from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import PageShell from './ProfessorPageShell';
import Button from '../../../components/shared/Button';
import { useAuth } from '../../../context/login/useAuth';
import { PROFESSOR_NAV_ITEMS } from '../../../data/navigationData';
import { useProfessorWorkflow } from '../../../context/professor/useProfessorWorkflow';
import { professorErrorMessage } from '../../../utils/professorErrors';
import { logProfessorError } from '../../../utils/professorDiagnostics';
import styles from '../styles/ProfessorDashboard.module.css';

const attentionLabels = { assessment: 'Assessments needing review', revision: 'Unpublished lesson revisions', approval: 'Modules awaiting approval' };
const shortDate = value => new Date(value + 'T00:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

export default function ProfessorDashboard() {
 const workflow = useProfessorWorkflow();
 const { getProfessorDashboardData } = workflow.api;
 const { user } = useAuth();
 const firstName = (workflow.target ?? user)?.name?.split(' ')[0] ?? 'there';
 const [isLoading, setIsLoading] = useState(true), [loadError, setLoadError] = useState(null), [retry, setRetry] = useState(0), [data, setData] = useState(null);
 useEffect(() => {
  const controller = new AbortController();
  getProfessorDashboardData({ signal: controller.signal }).then(result => { if (!controller.signal.aborted) setData(result); })
   .catch(error => { if (!controller.signal.aborted) { logProfessorError('load-dashboard', error); setLoadError(error); } })
   .finally(() => { if (!controller.signal.aborted) setIsLoading(false); });
  return () => controller.abort();
 }, [retry, getProfessorDashboardData]);
 const subjects = workflow.basePath + '/subjects', progress = workflow.basePath + '/progress';
 const statCards = [
  { id: 'totalStudents', icon: Users, label: 'My Students', to: progress },
  { id: 'totalSubjects', icon: BookOpen, label: 'My Subjects', to: subjects },
  { id: 'totalModules', icon: FileText, label: 'Total Modules', to: subjects },
  { id: 'approvedModules', icon: CheckCircle2, label: 'Approved Modules', to: subjects },
  { id: 'totalAssessments', icon: ClipboardCheck, label: 'Assessments', to: subjects },
  { id: 'totalPassedAttempts', icon: Award, label: 'Passed Attempts', to: progress },
 ];
 const performance = data?.performance ?? [], attention = data?.attention ?? [], recent = data?.recentActivity ?? [];
 return <PageShell responsive navItems={PROFESSOR_NAV_ITEMS} sectionLabel="Prof" roleBadge="Professor">
  <div className={styles.contentArea}>
   <header className={styles.topbar}><div className={styles.breadcrumb}><LayoutGrid size={16} /> Dashboard</div></header>
   <main className={styles.main}>
    <header className={styles.pageHeader}><div><h1 className={styles.greeting}>Teaching Overview</h1><p className={styles.subtext}>Welcome back, {firstName}. Your teaching, at a glance.</p></div><Link className={styles.manageLink} to={subjects}>My Subjects <ArrowRight size={16} /></Link></header>
    {isLoading ? <p className={styles.loadingText} role="status">Loading your teaching overview...</p> : loadError || !data ? <section className={styles.panel}><p role="alert">{professorErrorMessage(loadError, "Couldn't load the dashboard. Please try again.")}</p><Button variant="secondary" onClick={() => { setIsLoading(true); setLoadError(null); setRetry(n => n + 1); }}>Retry</Button></section> : <>
     <section className={styles.statsGrid} aria-label="Teaching summary">{statCards.map(({ id, icon: Icon, label, to }) => <Link key={id} to={to} className={styles.statCard}><span className={styles.statIcon}><Icon size={20} /></span><div><p className={styles.statValue}>{data[id] ?? 0}</p><p className={styles.statLabel}>{label}</p></div></Link>)}</section>
     <p className={styles.scopeNote}>Student metrics include active Students eligible for your subjects. Passed Attempts includes submitted passing attempts across quiz versions.</p>
     <div className={styles.overviewGrid}>
      <section className={styles.panel} aria-labelledby="performance-title">
       <div className={styles.sectionHeaderRow}><h2 id="performance-title" className={styles.sectionTitle}>Student Performance</h2><Link to={progress} className={styles.manageLink}>Student Progress <ArrowRight size={15} /></Link></div>
       <p className={styles.helper}>Daily average assessment score over the last 28 days. Each submitted attempt counts once; days without attempts are omitted.</p>
       {performance.length ? <>
        <div className={styles.chart} aria-label="Daily average assessment scores"><ResponsiveContainer width="100%" height="100%"><LineChart data={performance} accessibilityLayer margin={{ top: 12, right: 12, bottom: 4, left: -16 }}><CartesianGrid vertical={false} stroke="var(--color-border)" /><XAxis dataKey="day" tickFormatter={shortDate} tick={{ fill: 'var(--color-text-muted)', fontSize: 12 }} minTickGap={32} /><YAxis domain={[0, 100]} tickFormatter={value => value + '%'} tick={{ fill: 'var(--color-text-muted)', fontSize: 12 }} /><Tooltip labelFormatter={shortDate} formatter={value => [Number(value).toFixed(1) + '%', 'Average score']} contentStyle={{ background: 'var(--color-surface)', borderColor: 'var(--color-border)', color: 'var(--color-text-primary)', borderRadius: 'var(--radius-md)' }} /><Line type="linear" dataKey="averageScore" stroke="var(--color-primary)" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }} isAnimationActive={false} /></LineChart></ResponsiveContainer></div>
        <details className={styles.chartDetails}><summary>View performance data</summary><div className={styles.tableScroll}><table><caption>Submitted assessments in your academic scope</caption><thead><tr><th>Date</th><th>Average score</th><th>Attempts</th></tr></thead><tbody>{performance.map(row => <tr key={row.day}><td>{shortDate(row.day)}</td><td>{row.averageScore.toFixed(1)}%</td><td>{row.attempts}</td></tr>)}</tbody></table></div></details>
       </> : <div className={styles.empty}><ClipboardCheck size={28} /><h3>No assessment results yet</h3><p>Scores will appear here when active Students submit assessments in your subjects.</p><Link to={progress} className={styles.manageLink}>View Student Progress</Link></div>}
      </section>
      <section className={styles.panel} aria-labelledby="attention-title"><div className={styles.sectionHeaderRow}><h2 id="attention-title" className={styles.sectionTitle}>Needs Attention</h2><CircleAlert size={18} className={styles.mutedIcon} /></div><p className={styles.helper}>Your next teaching tasks, all in one place.</p>
       {attention.map(item => <div className={styles.attentionGroup} key={item.kind}><div className={styles.attentionHeading}><h3>{attentionLabels[item.kind]}</h3><span className={item.count ? styles.count : styles.zeroCount}>{item.count}</span></div>{item.count ? <><ul className={styles.lessonLinks}>{item.lessons.map(lesson => <li key={lesson.moduleId}><Link to={`${subjects}/${lesson.subjectId}/week/${lesson.weekId}`}>{lesson.title || 'Open lesson'}<ArrowRight size={14} /></Link></li>)}</ul>{item.count > item.lessons.length && <Link className={styles.manageLink} to={subjects}>View all in Subjects</Link>}</> : <p className={styles.helper}>Nothing pending</p>}</div>)}
       {!attention.length && <p className={styles.helper}>No pending teaching tasks.</p>}
      </section>
     </div>
     <section className={styles.panel} aria-labelledby="activity-title"><div className={styles.sectionHeaderRow}><h2 id="activity-title" className={styles.sectionTitle}>Recent Teaching Activity</h2><Clock3 size={18} className={styles.mutedIcon} /></div>{recent.length ? <ul className={styles.activityList}>{recent.map(item => <li key={item.id}><span className={styles.activityIcon}><FileText size={16} /></span><p>{item.description}</p><time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })}</time></li>)}</ul> : <p className={styles.emptyText}>Your published lessons, quiz approvals, and lesson revisions will appear here.</p>}</section>
    </>}
   </main>
  </div>
 </PageShell>;
}
