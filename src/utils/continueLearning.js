// Backend timestamps share the persisted business-time representation. Compare them
// without converting through the browser timezone; pad absent fractional seconds.
export function selectContinueLesson(groups) {
  const eligible = groups.flatMap(({ subject, weeks }) => weeks
    .filter(week => week.moduleId != null && week.unlocked && week.lessonAvailable)
    .map(week => ({ subject, week })));
  const stable = (a, b) => Number(a.subject.subjectId) - Number(b.subject.subjectId)
    || a.week.weekNumber - b.week.weekNumber || Number(a.week.weekId) - Number(b.week.weekId);
  const timestamp = item => typeof item.week.lastActivityAt === 'string'
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?$/.test(item.week.lastActivityAt)
    ? (item.week.lastActivityAt.includes('.') ? item.week.lastActivityAt : item.week.lastActivityAt + '.').padEnd(29, '0') : '';
  const recent = eligible.filter(timestamp).sort((a, b) => timestamp(b).localeCompare(timestamp(a))
    || Number(Boolean(a.week.completed)) - Number(Boolean(b.week.completed)) || stable(a, b));
  return recent[0] ?? eligible.filter(item => !item.week.completed).sort(stable)[0] ?? null;
}
