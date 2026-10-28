export function quizLoadError(error) {
  if (error?.response?.data?.code === 'ENROLLMENT_REQUIRED') return 'Enroll in this subject to access learning activities. Browse Subjects to enroll.';
  const status = error?.response?.status;
  if (status === 401) return 'Your session has expired or you are not signed in. Please sign in again to load this quiz.';
  if (status === 403) return 'You do not have access to this quiz. Return to Subjects to check your available lessons.';
  if (status === 404) return 'This quiz could not be found or is not available yet. Return to Subjects or try again later.';
  if (status >= 500) return 'The server could not load this quiz. Please try again shortly.';
  if (!error?.response && (error?.request || ['ERR_NETWORK', 'ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)))
    return 'Could not connect to HALO. Check your connection and try again.';
  return 'This quiz could not be loaded. Return to Subjects or try again later.';
}
