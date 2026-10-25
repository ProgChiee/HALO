export const recoveryRequestMessage = 'If this account can receive recovery email, a code will be sent. Check your inbox and spam folder.';

export function recoveryCooldown(value, fallback = 60) {
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds > 0 ? Math.min(86401, Math.ceil(seconds)) : fallback;
}

export function recoveryError(error) {
  const data = error?.response?.data;
  if (error?.response?.status === 429) {
    const seconds = recoveryCooldown(data?.retryAfterSeconds ?? error.response.headers?.['retry-after']);
    return { message: `Too many recovery attempts. Please wait ${seconds} seconds before trying again.`, seconds };
  }
  if (data?.code === 'INVALID_RESET_REQUEST') return { message: 'The code could not be verified. Check your email and code. If it has expired or reached its attempt limit, wait before requesting a new code.', seconds: 0 };
  if (data?.code === 'INVALID_NEW_PASSWORD') return { message: 'Choose a different password with at least 8 characters.', seconds: 0 };
  if (error?.response?.status === 400) return { message: 'Check your email, six-digit code, and password, then try again.', seconds: 0 };
  return { message: error?.response ? 'Password recovery is unavailable. Please try again later.' : 'Could not connect. Please check your connection and try again.', seconds: 0 };
}
