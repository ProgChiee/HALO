// Presentation only: older Mentor replies appended this machine-generated source footer.
// Do not strip arbitrary brackets, filenames, page references, or educational prose.
export function mentorAnswerText(text) {
  if (typeof text !== 'string') return '';
  return text.replace(/\s+Module sources: [^\r\n]+\.(?:pdf|png|jpe?g) \(page \d+\)(?:[;,] [^\r\n]+\.(?:pdf|png|jpe?g) \(page \d+\))*\s*$/, '');
}
