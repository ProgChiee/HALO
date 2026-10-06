// Load the published lesson before starting the potentially slow material index/session.
export function validateLessonRoute(module, weekId) {
  if (!module || String(module.weekId) !== String(weekId)
    || !/^[1-9]\d*$/.test(String(module.id)) || !/^[1-9]\d*$/.test(String(module.subjectId))
    || !Number.isInteger(module.weekNumber) || module.weekNumber <= 0) {
    throw Object.assign(new Error('Lesson response mismatch'), { code: 'INVALID_LESSON_RESPONSE' });
  }
  if (module.status !== 'APPROVED') throw Object.assign(new Error('Not published'), { code: 'LESSON_NOT_APPROVED' });
  if (module.aiGenerationStatus !== 'COMPLETED') throw Object.assign(new Error('Not generated'), { code: 'LESSON_NOT_GENERATED' });
}

export async function loadLessonChat({ subjectId, weekId, signal, getLesson, openMentor, onLesson, onMentor, onError, onCanonical }) {
  let stage = 'lesson';
  try {
    if (!/^[1-9]\d*$/.test(String(weekId))) throw Object.assign(new Error('Invalid lesson ID'), { code: 'MODULE_NOT_FOUND' });
    const module = await getLesson(weekId, { signal });
    if (signal.aborted) return;
    validateLessonRoute(module, weekId);
    if (String(subjectId) !== String(module.subjectId)) {
      if (!onCanonical) throw Object.assign(new Error('Subject route mismatch'), { code: 'INVALID_LESSON_RESPONSE' });
      onCanonical(module); return;
    }
    onLesson(module);
    stage = 'mentor';
    const conversation = await openMentor(module.id, { signal });
    if (signal.aborted) return;
    if (String(conversation?.moduleId) !== String(module.id) || !/^[1-9]\d*$/.test(String(conversation?.sessionId)) || !Array.isArray(conversation.messages)) {
      throw Object.assign(new Error('Mentor response mismatch'), { code: 'INVALID_MENTOR_RESPONSE' });
    }
    onMentor(conversation);
  } catch (error) {
    if (!signal.aborted) onError(error, stage);
  }
}
