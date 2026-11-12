import { useState } from 'react';
export function useSubjectPagination(items, resetKey = '') {
  const [size, setSize] = useState(5);
  const [selected, setSelected] = useState(1);
  const [previousKey, setPreviousKey] = useState(resetKey);
  const pages = Math.max(1, Math.ceil(items.length / size));
  const page = previousKey !== resetKey ? 1 : Math.min(selected, pages);
  if (previousKey !== resetKey) { setPreviousKey(resetKey); setSelected(1); }
  else if (selected !== page) setSelected(page);
  return { items: items.slice((page - 1) * size, page * size), total: items.length, page, pages, size,
    setPage: value => setSelected(Math.max(1, Math.min(value, pages))),
    setSize: value => { if ([5, 10, 20].includes(value)) { setSize(value); setSelected(1); } } };
}

