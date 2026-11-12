
import styles from './SubjectPagination.module.css';

export default function SubjectPagination({ pagination: p, label }) {
  const numbers = [...new Set([1, p.page - 1, p.page, p.page + 1, p.pages])]
    .filter(n => n >= 1 && n <= p.pages).sort((a, b) => a - b);
  return <nav className={styles.pagination} aria-label={label}>
    <p role="status">Showing {p.total ? (p.page - 1) * p.size + 1 : 0}–{Math.min(p.page * p.size, p.total)} of {p.total} subjects · Page {p.page} of {p.pages}</p>
    <label>Items per page <select value={p.size} onChange={e => p.setSize(Number(e.target.value))}>
      {[5, 10, 20].map(size => <option key={size} value={size}>{size}</option>)}
    </select></label>
    <div className={styles.controls}>
      <button type="button" disabled={p.page === 1} onClick={() => p.setPage(p.page - 1)}>Previous</button>
      {numbers.map((number, index) => <span key={number} className={styles.number}>
        {index > 0 && number > numbers[index - 1] + 1 && <span aria-hidden="true">…</span>}
        <button type="button" aria-label={`Page ${number}`} aria-current={p.page === number ? 'page' : undefined} onClick={() => p.setPage(number)}>{number}</button>
      </span>)}
      <button type="button" disabled={p.page === p.pages} onClick={() => p.setPage(p.page + 1)}>Next</button>
    </div>
  </nav>;
}
