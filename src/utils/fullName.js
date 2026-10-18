const MESSAGES = {
  FULL_NAME_REQUIRED: 'Full name is required.',
  FULL_NAME_TOO_LONG: 'Full name must be 100 characters or fewer.',
  INVALID_FULL_NAME: 'Use letters, spaces, hyphens, apostrophes, periods, and commas. Include at least one letter.',
  INVALID_PROFILE_UPDATE: 'Submit only a full name as text.',
};

export function validateFullName(input) {
  const value = (input ?? '').normalize('NFC').replace(/[\p{White_Space}\uFEFF]+/gu, ' ').trim();
  let code;
  if (!value) code = 'FULL_NAME_REQUIRED';
  else if (value.length > 100) code = 'FULL_NAME_TOO_LONG';
  else if (!/^[\p{L}\p{M} .,'\u2019-]+$/u.test(value) || !/\p{L}/u.test(value)) code = 'INVALID_FULL_NAME';
  return { value, error: MESSAGES[code] ?? '' };
}

export function fullNameErrorMessage(error) {
  const response = error?.response;
  if (MESSAGES[response?.data?.code]) return MESSAGES[response.data.code];
  if (response?.status === 401) return 'Your session has expired. Please sign in again.';
  if (response?.status === 403) return 'You do not have permission to edit this profile.';
  if (response?.status === 404 || response?.status === 405) return 'Profile editing is unavailable. Please refresh or contact an administrator.';
  if (response?.status === 400 || response?.status === 422) return 'The profile update was rejected. Please check your full name and try again.';
  if (response?.status === 409) return 'Your profile changed while saving. Please refresh and try again.';
  if (response?.status === 429) return 'Too many requests. Please wait a moment and try again.';
  if (response?.status >= 500) return 'The server could not save your full name. Please try again later or contact an administrator.';
  if (response) return 'The profile update request was unsuccessful. Please try again or contact an administrator.';
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') return 'The profile update timed out. Please check your profile before trying again.';
  if (error?.code === 'ERR_NETWORK' || error?.request) return 'Could not reach HALO. Please check your connection and try again.';
  if (error?.code === 'PROFILE_ACCOUNT_CHANGED') return 'Your signed-in account changed. Please reopen your profile.';
  return 'Could not finish updating your profile. Please refresh and try again.';
}
