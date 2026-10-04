const OPERATIONS = new Set(['load-dashboard', 'load-profile', 'save-subject', 'delete-subject', 'create-week']);
const CODES = new Set(['RESOURCE_NOT_FOUND', 'VALIDATION_FAILED', 'ACCESS_DENIED', 'AUTHENTICATION_REQUIRED', 'INVALID_OR_EXPIRED_TOKEN']);

// Never forward error objects, messages, URLs, config, or arbitrary response values.
export function logProfessorError(operation, error) {
  const status = error?.response?.status;
  const code = error?.response?.data?.code;
  console.error('[HALO Professor]', {
    operation: OPERATIONS.has(operation) ? operation : 'request-failed',
    ...(Number.isInteger(status) && status >= 100 && status <= 599 ? { status } : {}),
    ...(CODES.has(code) ? { code } : {}),
  });
}
