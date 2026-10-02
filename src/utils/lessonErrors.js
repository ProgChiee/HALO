const reasons = {
  MODULE_MATERIALS_REQUIRED: 'This module needs uploaded learning materials before its mentor chat can open. Contact your professor.',
  MODULE_MATERIAL_UNREADABLE: 'A learning material could not be read. Ask your professor to check the uploaded files.',
  MODULE_MATERIALS_TOO_LARGE: 'This module exceeds the material indexing limit. Ask your professor to split the lesson materials.',
  MODULE_MATERIAL_UNSUPPORTED: 'A learning material uses an unsupported file format.',
  MODULE_MATERIAL_MISMATCH: 'The lesson material does not belong to this module. Contact your administrator.',
  MODULE_INDEX_UNAVAILABLE: 'The module material index could not be prepared. Please retry when the AI service is available.',
  AUTHENTICATION_REQUIRED: 'Please sign in again to open this lesson.',
  INVALID_OR_EXPIRED_TOKEN: 'Your session expired. Please sign in again.',
  INVALID_ROLE: 'Sign in with a student account to open this lesson.',
  INVALID_ROLE_OR_ACCESS: 'Your account does not have access to this lesson.',
  ACCOUNT_INACTIVE: 'Your account is inactive. Contact your administrator.',
  STUDENT_NOT_ENROLLED: 'This subject is not assigned to your student year level.',
  LESSON_NOT_APPROVED: 'This lesson has not been published by your professor.',
  LESSON_NOT_GENERATED: 'This lesson is not ready. Ask your professor to regenerate and publish it.',
  PREVIOUS_WEEK_INCOMPLETE: 'Complete the previous week before opening this lesson.',
  MODULE_NOT_FOUND: 'The learning module was not found. Reopen the lesson from Subjects.',
  MODULE_WEEK_NOT_FOUND: 'This lesson no longer belongs to the selected subject.',
  SESSION_NOT_OWNED: 'This conversation belongs to another student.',
  SESSION_NOT_FOUND: 'This conversation no longer exists. Reopen the lesson.',
  METHOD_NOT_ALLOWED: 'The request method does not match the server. Refresh the app and check that the updated backend is running.',
  MENTOR_PROVIDER_UNAVAILABLE: 'The AI mentor is temporarily unavailable. Your published lesson is still available to read.',
  MENTOR_EMPTY_RESPONSE: 'The AI mentor returned no reply. Please try again.',
};

export function lessonErrorMessage(error, stage = 'mentor') {
  const code = error.response?.data?.code ?? error.code;
  if (reasons[code]) return reasons[code];
  if (code === 'INVALID_LESSON_RESPONSE') return 'Failed to load lesson: the server returned an invalid or mismatched lesson.';
  if (code === 'INVALID_MENTOR_RESPONSE') return 'AI Mentor unavailable: the server returned a mismatched session.';
  if (code === 'ECONNABORTED' || code === 'ETIMEDOUT') return stage === 'lesson'
    ? 'Backend unavailable: the lesson request timed out. Please retry.'
    : 'AI Mentor unavailable: the request timed out. You can still read the lesson. Please retry.';
  if (code === 'ERR_NETWORK' || (!error.response && error.request)) return 'Backend unavailable. Check your connection and try again.';
  const status = error.response?.status;
  if (status === 401) return reasons.AUTHENTICATION_REQUIRED;
  if (status === 403) return reasons.INVALID_ROLE_OR_ACCESS;
  if (status === 404) return reasons.MODULE_NOT_FOUND;
  if (stage === 'lesson' && (status === 502 || status === 503 || status === 504)) return 'Backend unavailable. Please retry.';
  if (status === 502 || status === 503) return reasons.MENTOR_PROVIDER_UNAVAILABLE;
  return stage === 'lesson' ? 'Failed to load lesson. Please retry.' : 'AI Mentor unavailable. Please retry.';
}
