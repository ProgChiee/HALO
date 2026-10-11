# Subjects / Weeks / Modules visual polish

## Files changed

- `src/pages/professor/styles/SubjectManagement.module.css`
- `src/pages/student/styles/Subjects.module.css`
- `src/pages/professor/styles/LessonEditor.module.css`
- `src/pages/student/styles/LessonChat.module.css`

## Findings and improvements

Student subject counts were absolutely positioned beside centered titles, risking overlap. Headers now wrap naturally with counts in normal flow. Long subject/week titles and filenames can wrap. Locked-week icons no longer use a strong red fill; current/completed rows use restrained borders and existing semantic chips. Professor destructive controls use quieter surfaces. Week rows and source/action groups wrap, empty-week text reuses the shared empty primitive, and generated review actions have a subtle separator. Published lesson subsection spacing is clearer and existing focused reading widths remain.

## Responsive review

Static CSS/markup review addressed long names, counts, filenames, and action wrapping. Existing 768px compact padding and layout breakpoints are retained. Live desktop/tablet/mobile browser verification was not performed.

## Preserved

Expandable subject/week lists remain lists rather than introducing a card-grid redesign. Acting modes inherit reused Professor/Student pages. No JSX, tokens, navigation, routes, APIs, permissions, ownership, progression, quiz/Mentor logic, session behavior, or dialog behavior changed. No new status data or empty-state business logic was added.

## Results

- Frontend tests: 148 passed, 0 failed.
- Lint: passed.
- Production build: passed.
