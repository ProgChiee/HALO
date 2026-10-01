import { useState, useRef, useMemo } from 'react';
import { Users, Search, Eye, Power, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { useToast } from '../../../context/notifications/useToast';
import { useAuth } from '../../../context/login/useAuth';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getStudents, toggleStudentStatus } from '../../../services/admin/adminService';
import { useRemoteData } from '../../../hooks/useRemoteData';
import { STATUS_LABELS } from '../../../utils/backendContract';
import styles from '../styles/StudentManagement.module.css';

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
      s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.email.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [students, searchQuery]);

  async function handleToggleStatus(student) {
    if (toggleLock.current) return;
    toggleLock.current = true;
    setTogglingId(student.id);
    try {
      const updated = await toggleStudentStatus(student.id);
      setStudents((previous) => previous.map((item) => item.id === updated.id ? updated : item));
      setViewingStudent((previous) => previous?.id === updated.id ? updated : previous);
      showToast('Account status: ' + (STATUS_LABELS[updated.status] ?? updated.status), 'success');
    } catch {
      showToast("Couldn't update the account status. Please try again.", 'error');
    } finally {
      toggleLock.current = false;
      setTogglingId(null);
    }
  }

  return (
    <PageShell
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
            placeholder="Search students"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>

        {isLoading ? (
          <p className={styles.loadingText}>Loading students...</p>
        ) : loadError ? (
          <div className={styles.tableCard} style={{ padding: '3rem', textAlign: 'center' }}>
            <p className={styles.loadingText}>Couldn't load students. Please check your connection.</p>
            <button
              onClick={loadStudents}
              style={{ marginTop: '12px', color: 'var(--color-accent-active)', textDecoration: 'underline', background: 'none', border: 'none', cursor: 'pointer' }}
            >
              Retry
            </button>
          </div>
        ) : (
          <div className={styles.tableCard}>
            <div className={styles.tableHeaderRow}>
              <span>Name</span>
              <span>Email</span>
              <span>Year</span>
              <span>Progress</span>
              <span>Status</span>
              <span>Actions</span>
            </div>

            {filteredStudents.map((student) => (
              <div
                key={student.id}
                className={`${styles.tableRow} ${styles.tableRowClickable}`}
                onClick={() => setViewingStudent(student)}
              >
                <span className={styles.studentName}>{student.name}</span>
                <span className={styles.studentEmail}>{student.email}</span>
                <span className={styles.studentYear}>{student.yearLevel === 'FIRST_YEAR' ? '1st Year' : '2nd Year'}</span>
                <span className={styles.studentProgress}>—</span>
                <span className={`${styles.statusText} ${styles[`status_${student.status?.toLowerCase()}`]}`}>
                  {STATUS_LABELS[student.status] ?? student.status}
                </span>
                <span className={styles.actions} onClick={(e) => e.stopPropagation()}>
                  <button
                    className={styles.actionBtn}
                    onClick={() => setViewingStudent(student)}
                    aria-label="View"
                  >
                    <Eye size={15} />
                  </button>
                  <button
                    className={`${styles.actionBtn} ${student.status !== 'ACTIVE' ? styles.actionBtnOff : ''}`}
                    onClick={() => handleToggleStatus(student)}
                    disabled={togglingId !== null}
                    aria-label={student.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
                  >
                    <Power size={15} />
                  </button>
                </span>
              </div>
            ))}

            {filteredStudents.length === 0 && (
              <p className={styles.emptyState}>No students match your search.</p>
            )}
          </div>
        )}
      </main>

      {viewingStudent && (
        <div className={styles.modalOverlay} onClick={() => setViewingStudent(null)}>
          <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <div className={styles.profileHeaderRow}>
                <div className={styles.profileAvatar}>{viewingStudent.name.charAt(0)}</div>
                <div>
                  <h3 className={styles.modalTitle}>{viewingStudent.name}</h3>
                  <p className={styles.profileEmail}>{viewingStudent.email}</p>
                </div>
              </div>
              <button className={styles.closeBtn} onClick={() => setViewingStudent(null)} aria-label="Close">
                <X size={18} />
              </button>
            </div>

            <div className={styles.detailList}>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Year Level</span>
                <span className={styles.detailValue}>{viewingStudent.yearLevel === 'FIRST_YEAR' ? '1st Year' : '2nd Year'}</span>
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
          </div>
        </div>
      )}
    </PageShell>
  );
}