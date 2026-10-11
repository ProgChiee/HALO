const OPERATIONS = new Set(['record-lesson-study', 'load-dashboard', 'load-continue', 'load-subjects', 'load-quiz', 'start-quiz', 'submit-quiz', 'load-progress', 'load-badges', 'load-profile']);
const CODES = new Set([
  'AUTHENTICATION_REQUIRED', 'INVALID_OR_EXPIRED_TOKEN', 'ACCESS_DENIED',
  'INVALID_ROLE', 'INVALID_ROLE_OR_ACCESS', 'ACCOUNT_INACTIVE', 'STUDENT_NOT_ENROLLED',
  'RESOURCE_NOT_FOUND', 'MODULE_NOT_FOUND', 'SUBJECT_NOT_FOUND', 'MODULE_WEEK_NOT_FOUND',
  'LESSON_NOT_APPROVED', 'LESSON_NOT_GENERATED', 'PREVIOUS_WEEK_INCOMPLETE',
  'ASSESSMENT_NOT_AVAILABLE', 'ASSESSMENT_ALREADY_SUBMITTED', 'INVALID_ANSWER_SET',
  'ANSWERS_ALREADY_RECORDED', 'VALIDATION_ERROR', 'INTERNAL_SERVER_ERROR',
  'MODULE_INDEX_UNAVAILABLE',
]);

// Only fixed labels and allowlisted scalar values may reach the console.
export function logStudentError(operation, error) {
  const status = error?.response?.status;
  const code = error?.response?.data?.code;
  console.error('[HALO Student]', {
    operation: OPERATIONS.has(operation) ? operation : 'request-failed',
    ...(Number.isInteger(status) && status >= 100 && status <= 599 ? { status } : {}),
    ...(CODES.has(code) ? { code } : {}),
  });
}
