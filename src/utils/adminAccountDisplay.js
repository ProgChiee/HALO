// Preserve valid text exactly; fallbacks are presentation-only.
export const accountText = value => typeof value === 'string' ? value : '';
export const accountDisplay = (value, fallback = 'Unknown name') => accountText(value).trim() ? value : fallback;
export function accountInitial(value, stripTitle = false) {
  const text = accountText(value);
  return (stripTitle ? text.replace(/^(Dr\.|Prof\.)\s*/, '') : text).charAt(0).trim() || '?';
}
export function accountTick(value) {
  const text = accountDisplay(value);
  return text.length > 22 ? text.slice(0, 22) + '…' : text;
}
