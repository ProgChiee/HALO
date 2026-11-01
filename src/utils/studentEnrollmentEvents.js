const eventName = 'halo:student-enrollment-changed';

// Presentation invalidation only. Every reload still uses the authenticated API.
export function notifyStudentEnrollmentChanged() {
  window.dispatchEvent(new Event(eventName));
}

export function onStudentEnrollmentChanged(listener) {
  window.addEventListener(eventName, listener);
  return () => window.removeEventListener(eventName, listener);
}
