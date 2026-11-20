// Only unambiguous, exact matches on the same extracted page receive a selectable overlay.
export function locateFragment(text, value, page, owners) {
  if (!value.trim()) return null;
  const start = text.indexOf(value);
  const end = start + value.length;
  if (start < 0 || text.indexOf(value, start + 1) >= 0 ||
      !owners.some(s => s.page === page && s.start <= start && end <= s.end)) return null;
  return { page, start, end };
}

