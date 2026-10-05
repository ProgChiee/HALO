export function shouldReconcileQuiz(error) {
  const status = error?.response?.status;
  const code = error?.response?.data?.code;
  if (error?.code === 'ERR_CANCELED') return false;
  if (status) return status === 502 || status === 503 || status === 504
    || (status === 409 && code === 'ASSESSMENT_ALREADY_SUBMITTED');
  return ['ECONNABORTED', 'ETIMEDOUT', 'ERR_NETWORK'].includes(error?.code)
    || Boolean(error?.request);
}

export function isSavedQuizResult(result, attemptId) {
  return String(result?.attemptId) === String(attemptId)
    && Number.isFinite(result?.score) && result.score >= 0 && result.score <= 100
    && typeof result?.passed === 'boolean';
}
