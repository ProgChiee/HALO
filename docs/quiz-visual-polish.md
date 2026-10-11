# Quiz visual polish

## Source file changed

- `src/pages/student/styles/Quiz.module.css`

## Findings and improvements

Intro/result cards had excessive internal padding on narrow screens; option hover barely differed from default; selected markers used a solid fill; disabled options lacked a local visual treatment.

Cards now use controlled token-based padding, with compact mobile padding. Questions and review text wrap safely within the existing focused container. Options are full-width with regular-weight answer text, subtle hover, soft-green selection, outlined selected markers, visible keyboard focus, and disabled opacity/cursor. Existing aria-pressed semantics are unchanged.

Progress uses primary green on a neutral track without width animation. Navigation has a subtle separator and mobile full-width buttons. Review rows have consistent spacing and wrapping while keeping existing correct/incorrect feedback and disclosure rules.

## Preserved and scope

No JSX, token, route, API, validation, attempt, submission/reconciliation, ownership, badge, or progress logic changed. No Previous control, confirmation step, or new progress mechanism was added. Admin Student Mode reuses Quiz and inherits the styles. No Professor assessment preview imports this stylesheet, so no Professor preview was modified.

## Verification

- Full frontend suite: 148 passed, 0 failed.
- Lint: passed.
- Production build: passed.
- Responsive CSS/markup reviewed; live desktop/tablet/mobile rendering remains unverified.
