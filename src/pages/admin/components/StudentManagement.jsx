import { useState, useEffect, useMemo } from 'react';
import { Users, Search, Eye, Power, X } from 'lucide-react';
import PageShell from '../../../components/shared/PageShell';
import { useToast } from '../../../context/notifications/ToastContext';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import { getStudents, toggleStudentStatus } from '../../../services/admin/adminService';
import styles from '../styles/StudentManagement.module.css';

export default function StudentManagement() {
  const { showToast } = useToast();

  const [isLoading, setIsLoading] = useState(true);
  const [students, setStudents] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [viewingStudent, setViewingStudent] = useState(null);

  useEffect(() => {
    loadStudents();
  }, []);

  async function loadStudents() {
    setIsLoading(true);
    const data = await getStudents();
    setStudents(data);
    setIsLoading(false);
  }

  const filteredStudents = useMemo(() => {
    return students.filter((s) =>
      s.fullName.toLowerCase().includes(searchQuery.toLowerCase()) ||
      s.email.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [students, searchQuery]);

  async function handleToggleStatus(student) {
    await toggleStudentStatus(student.id);
    showToast(
      student.status === 'active' ? 'Student deactivated.' : 'Student activated.',
      'success'
    );
    loadStudents();
  }

  return (
    <PageShell navItems={ADMIN_NAV_ITEMS} sectionLabel="Admin" roleBadge="Admin">
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
                <span className={styles.studentName}>{student.fullName}</span>
                <span className={styles.studentEmail}>{student.email}</span>
                <span className={styles.studentYear}>{student.year}</span>
                <span className={styles.studentProgress}>{student.progress}%</span>
                <span className={`${styles.statusText} ${styles[`status_${student.status}`]}`}>
                  {student.status === 'active' ? 'Active' : 'Inactive'}
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
                    className={`${styles.actionBtn} ${student.status === 'inactive' ? styles.actionBtnOff : ''}`}
                    onClick={() => handleToggleStatus(student)}
                    aria-label={student.status === 'active' ? 'Deactivate' : 'Activate'}
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
                <div className={styles.profileAvatar}>{viewingStudent.fullName.charAt(0)}</div>
                <div>
                  <h3 className={styles.modalTitle}>{viewingStudent.fullName}</h3>
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
                <span className={styles.detailValue}>{viewingStudent.year}</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Overall Progress</span>
                <span className={styles.detailValue}>{viewingStudent.progress}%</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailLabel}>Status</span>
                <span className={`${styles.detailValue} ${styles[`status_${viewingStudent.status}`]}`}>
                  {viewingStudent.status === 'active' ? 'Active' : 'Inactive'}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}