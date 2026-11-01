export const professorEnrollmentOptions = [
  ['ALL', 'All Enrollment Statuses'],
  ['ACTIVE', 'Actively Enrolled'],
  ['NEVER', 'Never Enrolled'],
  ['PREVIOUS', 'Previously Enrolled'],
];
export function professorEnrollmentLabel(value) {
  return professorEnrollmentOptions.find(([code]) => code === value)?.[1] ?? 'Enrollment status unavailable';
}
