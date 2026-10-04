// Values and shapes verified against the Java DTOs/enums in HALO.zip.
export const ROLE_FROM_API = Object.freeze({
  SUPER_ADMIN: 'superadmin', ADMIN: 'admin', PROFESSOR: 'professor', STUDENT: 'student',
});

export const STATUS_LABELS = Object.freeze({
  ACTIVE: 'Active', INACTIVE: 'Inactive', BLOCKED: 'Blocked',
});

export function parseLoginResponse(response) {
  const role = Object.hasOwn(ROLE_FROM_API, response?.role) ? ROLE_FROM_API[response.role] : null;
  if (!role || typeof response.token !== 'string' || !response.token.trim()
    || typeof response.name !== 'string' || typeof response.email !== 'string') {
    throw new Error('Invalid login response');
  }
  return {
    user: { name: response.name, email: response.email, role, backendRole: response.role, mustChangePassword: response.mustChangePassword === true },
    token: response.token,
  };
}

export function findAvailableWeek(weeks) {
  return [...weeks].sort((a, b) => a.weekNumber - b.weekNumber)
    .find((week) => week.unlocked && week.lessonAvailable && !week.completed);
}

export function buildAssessmentAnswers(questions, selectedAnswers) {
  if (!questions.length) throw new Error('Assessment has no questions');
  return questions.map(({ id }) => {
    const answer = selectedAnswers[id];
    if (!['A', 'B', 'C', 'D'].includes(answer)) {
      throw new Error('Answer every question before submitting');
    }
    return { questionId: id, answer };
  });
}
