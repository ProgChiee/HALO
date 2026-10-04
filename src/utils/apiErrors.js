const SAFE_MESSAGES = {
  EMAIL_ALREADY_EXISTS: 'An account with this email already exists.',
  ADMIN_NOT_FOUND: 'Admin account not found.',
  VALIDATION_FAILED: 'Please check the required fields, email format, password length, and selected values.',
  SUPER_ADMIN_REQUEST_FAILED: 'Unable to complete the request. Please try again.',
  ACTIVITY_LOGS_UNAVAILABLE: 'Activity logs are temporarily unavailable.',
};
const FIELD_MESSAGES = {
  name: 'Name is required and must be at most 100 characters.',
  email: 'Enter a valid email address of at most 254 characters.',
  password: 'Password is required and must be 12?72 characters, within 72 UTF-8 bytes.',
  passwordWithinByteLimit: 'Password must be at most 72 UTF-8 bytes.',
  status: 'Choose ACTIVE or INACTIVE.',
  supportedStatus: 'Choose ACTIVE or INACTIVE.',
  currentPassword: 'Enter your current password.',
  newPassword: 'New password must be 8?72 characters, within 72 UTF-8 bytes.',
  newPasswordWithinByteLimit: 'New password must be at most 72 UTF-8 bytes.',
};
export function apiErrorMessage(error, fallback = 'The request failed. Please try again.') {
  const body = error?.response?.data;
  if (body?.code === 'VALIDATION_FAILED' && body.errors) {
    const messages = [...new Set(Object.keys(body.errors).map(field => FIELD_MESSAGES[field]).filter(Boolean))];
    if (messages.length) return messages.join(' ');
  }
  if (SAFE_MESSAGES[error?.response?.data?.code]) return SAFE_MESSAGES[error.response.data.code];
  return ['ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)
    ? 'The request took too long. Please try again.' : fallback;
}
