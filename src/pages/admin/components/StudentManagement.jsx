import Dialog from '../../../components/shared/Dialog';
import { accountText, accountDisplay, accountInitial } from '../../../utils/adminAccountDisplay';
import { adminErrorMessage } from '../../../utils/adminErrors';
import { useState, useRef, useMemo } from 'react';
import { Users, Search, Eye, Power, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { useToast } from '../../../context/notifications/useToast';
import { useAuth } from '../../../context/login/useAuth';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getStudents, setStudentStatus } from '../../../services/admin/adminService';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/StudentManagement.module.css';

function yearLevelLabel(value) {
  if (value === 'FIRST_YEAR') return '1st Year';
  if (value === 'SECOND_YEAR') return '2nd Year';
  return 'Not specified';
}

export default function StudentManagement() {
  const { showToast } = useToast();
  const { role } = useAuth();
  const isSuperAdmin = role === 'superadmin';

  const { data: students, setData: setStudents, isLoading, error: loadError, reload: loadStudents } = useRemoteData(getStudents, []);
  const toggleLock = useRef(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewingStudent, setViewingStudent] = useState(null);
  const [togglingId, setTogglingId] = useState(null);


  const filteredStudents = useMemo(() => {
    return students.filter((s) =>
      accountText(s.name).toLowerCase().includes(searchQuery.toLowerCase()) ||
      accountText(s.email).toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [students, searchQuery]);

  async function handleToggleStatus(student) {
    if (toggleLock.current) return;
    toggleLock.current = true;
    setTogglingId(student.id);
    try {
      const updated = await setStudentStatus(student.id, student.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE');
      setStudents((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      setViewingStudent((previous) => previous?.id === updated.id ? updated : previous);
      showToast('Account status: ' + (STATUS_LABELS[updated.status] ?? updated.status), 'success');
    } catch (error) {
      showToast(adminErrorMessage(error), 'error');
    } finally {
      toggleLock.current = false;
      setTogglingId(null);
    }
  }

  return (
    <PageShell responsive
      navItems={isSuperAdmin ? SUPERADMIN_NAV_ITEMS : ADMIN_NAV_ITEMS}
      sectionLabel={isSuperAdmin ? 'Superadmin' : 'Admin'}
      roleBadge={isSuperAdmin ? 'Superadmin' : 'Admin'}
    >
      <header className={styles.topbar}>
        <div className={styles.breadcrumb}>
          <Users size={16} />
          Student Management
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.searchWrapper}>
          <Search size={18} className={styles.searchIcon} />
          <input
            type="text"
            className={styles.searchInput}
            aria-label="Search students"
              placeholder="Search students"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        {isLoading ? (
          <p className={styles.loadingText}>Loading students...</p>
        ) : loadError ? (
          <div className={styles.tableCard} style={{ padding: 'var(--space-12)', textAlign: 'center' }}>
            <p className={styles.loadingText}>Couldn't load students. Please check your connection.</p>
            <button
              onClick={loadStudents}
              style={{ marginTop: '12px', color: 'var(--color-accent-active)', textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}
            >
              Retry
            </button>
          </div>
        ) : (
          <div className={styles.tableCard} role="region" aria-label="Accounts table, scroll horizontally" tabIndex={0}>
            <div role="table" aria-label="Students">
            <div role="row" className={styles.tableHeaderRow}>
              <span role="columnheader" id="student-column-1">Name</span>
              <span role="columnheader" id="student-column-2">Email</span>
              <span role="columnheader" id="student-column-3">Year</span>
              <span role="columnheader" id="student-column-4">Progress</span>
              <span role="columnheader" id="student-column-5">Account Action</span>
              <span role="columnheader" id="student-column-6">Actions</span>
            </div>

            {filteredStudents.map((student) => (
              <div
                role="row"
                key={student.id}
                className={`${styles.tableRow} ${styles.tableRowClickable}`}
                onClick={() => setViewingStudent(student)}
              >
                <span role="cell" aria-describedby="student-column-1" className={styles.studentName}>{accountDisplay(student.name)}</span>
                <span role="cell" aria-describedby="student-column-2" className={styles.studentEmail}>{accountDisplay(student.email, 'Email not provided')}</span>
                <span role="cell" aria-describedby="student-column-3" className={styles.studentYear}>{yearLevelLabel(student.yearLevel)}</span>
                <span role="cell" aria-describedby="student-column-4" className={styles.studentProgress}>—</span>
                <span role="cell" aria-describedby="student-column-5" onClick={(e) => e.stopPropagation()}>
                  <button
                    className={`${styles.actionBtn} ${student.status === 'ACTIVE' ? styles.actionBtnOff : ''}`}
                    onClick={() => handleToggleStatus(student)}
                    disabled={togglingId !== null}
                    aria-label={student.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                  >
                    <Power size={15} aria-hidden="true" />
                    {student.status === 'ACTIVE' ? 'Deactivate' : 'Reactivate'}
                  </button>
                </span>
                <span role="cell" aria-describedby="student-column-6" className={styles.actions} onClick={(e) => e.stopPropagation()}>
                  <button
                    className={styles.actionBtn}
                    onClick={() => setViewingStudent(student)}
                    aria-label="View"
                  >
                    <Eye size={15} aria-hidden="true" /> View
                  </button>

                </span>
              </div>
            ))}

            {filteredStudents.length === 0 && (
              <p className={styles.emptyState}>No students match your search.</p>
            )}
            </div>
          </div>
        )}
      </main>

      {viewingStudent && (
        <Dialog labelledBy="student-details-title" onClose={() => setViewingStudent(null)} className={styles.modal} initialFocus="button">
            <div className={styles.modalHeader}>
              <div className={styles.profileHeaderRow}>
                <div className={styles.profileAvatar}>{accountInitial(viewingStudent.name)}</div>
                <div>
                  <h3 id="student-details-title" className={styles.modalTitle}>{accountDisplay(viewingStudent.name)}</h3>
                  <p className={styles.profileEmail}>{accountDisplay(viewingStudent.email, 'Email not provided')}</p>
                </div>
              </div>
              <button className={styles.closeBtn} onClick={() => setViewingStudent(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <div className={styles.detailList}>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Year Level</span>
                <span className={styles.detailValue}>{yearLevelLabel(viewingStudent.yearLevel)}</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Overall Progress</span>
                <span className={styles.detailValue}>—</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Status</span>
                <span className={`${styles.detailValue} ${styles[`status_${viewingStudent.status?.toLowerCase()}`]}`}>
                  {STATUS_LABELS[viewingStudent.status] ?? viewingStudent.status}
                </span>
              </div>
            </div>
        </Dialog>
      )}
    </PageShell>
  );
}