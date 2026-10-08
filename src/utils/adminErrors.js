const MESSAGES = {
  RESOURCE_NOT_FOUND: 'This account or resource no longer exists. Reload the list.',
  INVALID_TARGET_ROLE: 'This account does not have the required role for this action. Reload the list.',
  PROFESSOR_ID_CONFLICT: 'This Professor ID is already in use. Enter a different Professor ID.',
  EMAIL_ALREADY_EXISTS: 'An account with this email already exists. Use a different email address.',
  VALIDATION_FAILED: 'Check the required fields, email format, and field lengths.',
  ADMIN_REQUEST_FAILED: 'The server could not complete the request. Please try again later.',
  AUTHENTICATION_REQUIRED: 'Please sign in to continue.',
  ACCESS_DENIED: 'You do not have permission to perform this action.',
};

export function adminErrorMessage(error, fallback = 'The request failed. Please try again.') {
  const code = error?.response?.data?.code;
  if (Object.hasOwn(MESSAGES, code)) return MESSAGES[code];
  const status = error?.response?.status;
  if (status === 401) return MESSAGES.AUTHENTICATION_REQUIRED;
  if (status === 403) return MESSAGES.ACCESS_DENIED;
  if (status === 404) return MESSAGES.RESOURCE_NOT_FOUND;
  if (status === 409) return 'This change conflicts with existing data. Reload and check your entries.';
  if (status === 400) return MESSAGES.VALIDATION_FAILED;
  if (status >= 500) return MESSAGES.ADMIN_REQUEST_FAILED;
  if (['ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)) return 'The request timed out. Check the current account state before retrying.';
  if (error?.code === 'ERR_NETWORK') return 'Cannot reach the server. Check your connection and retry.';
  return fallback;
}
