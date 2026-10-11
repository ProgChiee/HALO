import { useCallback, useEffect, useRef, useState } from 'react';

export function useSubjectWeeks(loader) {
  const [weeksBySubject, setWeeksBySubject] = useState({});
  const [weeksLoading, setWeeksLoading] = useState({});
  const [weekErrors, setWeekErrors] = useState({});
  const requests = useRef(new Map());

  useEffect(() => {
    const pending = requests.current;
    return () => {
      pending.forEach((controller) => controller.abort());
      pending.clear();
    };
  }, []);

  const loadWeeks = useCallback(async (subjectId, refresh = false) => {
    const previousRequest = requests.current.get(subjectId);
    if (previousRequest && !refresh) return false;
    previousRequest?.abort();
    const controller = new AbortController();
    requests.current.set(subjectId, controller);
    setWeeksLoading((previous) => ({ ...previous, [subjectId]: true }));
    setWeekErrors((previous) => ({ ...previous, [subjectId]: false }));
    try {
      const weeks = await loader(subjectId, { signal: controller.signal });
      if (controller.signal.aborted) return false;
      setWeeksBySubject((previous) => ({ ...previous, [subjectId]: weeks }));
      return true;
    } catch {
      if (!controller.signal.aborted) setWeekErrors((previous) => ({ ...previous, [subjectId]: true }));
      return false;
    } finally {
      if (requests.current.get(subjectId) === controller) requests.current.delete(subjectId);
      if (!controller.signal.aborted) setWeeksLoading((previous) => ({ ...previous, [subjectId]: false }));
    }
  }, [loader]);

  function replaceWeek(subjectId, week) {
    requests.current.get(subjectId)?.abort();
    requests.current.delete(subjectId);
    setWeeksLoading(previous => ({ ...previous, [subjectId]: false }));
    setWeeksBySubject(previous => ({ ...previous, [subjectId]: previous[subjectId]?.map(item => item.id === week.id ? week : item) ?? [] }));
  }

  return { weeksBySubject, weeksLoading, weekErrors, loadWeeks, replaceWeek };
}
