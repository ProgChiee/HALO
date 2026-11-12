import { useEffect } from 'react';
import styles from '../styles/StudentProgress.module.css';

// API pages are zero-based. Never slice the already paginated records.
export default function StudentTablePagination({ data, page, size, onPageChange, onSizeChange, label }) {
  const total = data.totalElements;
  const pages = Math.max(1, data.totalPages);
  const validPage = Math.min(page, pages - 1);
  useEffect(() => {
    if (page !== validPage) onPageChange(validPage);
  }, [page, validPage, onPageChange]);
  const numbers = [...new Set([0, validPage - 1, validPage, validPage + 1, pages - 1])]
    .filter(n => n >= 0 && n < pages).sort((a, b) => a - b);
  return <nav className={styles.studentPagination} aria-label={label}>
    <p role="status">Showing {total ? validPage * size + 1 : 0}–{Math.min((validPage + 1) * size, total)} of {total} students · Page {validPage + 1} of {pages}</p>
    <label>Rows per page <select value={size} onChange={e => onSizeChange(Number(e.target.value))}>
      {[5, 10, 20].map(value => <option key={value} value={value}>{value}</option>)}
    </select></label>
    <div className={styles.pageButtons}>
      <button type="button" disabled={validPage === 0} onClick={() => onPageChange(validPage - 1)}>Previous</button>
      {numbers.map((number, index) => <span key={number}>
        {index > 0 && number > numbers[index - 1] + 1 && <span aria-hidden="true">…</span>}
        <button type="button" aria-label={`Page ${number + 1}`} aria-current={number === validPage ? 'page' : undefined} onClick={() => onPageChange(number)}>{number + 1}</button>
      </span>)}
      <button type="button" disabled={validPage === pages - 1} onClick={() => onPageChange(validPage + 1)}>Next</button>
    </div>
  </nav>;
}
