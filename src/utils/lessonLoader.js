// Load the published lesson before starting the potentially slow material index/session.
export async function loadLessonChat({ weekId, signal, getLesson, openMentor, onLesson, onMentor, onError }) {
  let stage = 'lesson';
  try {
    if (!/^[1-9]\d*$/.test(String(weekId))) throw Object.assign(new Error('Invalid lesson ID'), { code: 'MODULE_NOT_FOUND' });
    const module = await getLesson(weekId, { signal });
    if (signal.aborted) return;
    if (!module || String(module.weekId) !== String(weekId) || !/^[1-9]\d*$/.test(String(module.id))) {
      throw Object.assign(new Error('Lesson response mismatch'), { code: 'INVALID_LESSON_RESPONSE' });
    }
    if (module.status !== 'APPROVED') throw Object.assign(new Error('Not published'), { code: 'LESSON_NOT_APPROVED' });
    if (module.aiGenerationStatus !== 'COMPLETED') throw Object.assign(new Error('Not generated'), { code: 'LESSON_NOT_GENERATED' });
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
