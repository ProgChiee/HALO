# Lesson Chat visual polish

## Source file changed

- `src/pages/student/styles/LessonChat.module.css`

## Findings and changes

Student and Mentor bubbles previously used similar white surfaces. Student messages now use primary-soft green; Mentor messages retain white and a subtle border. Messages use content-sized widths capped at 68ch, safe long-token wrapping, consistent internal padding, and restrained sender labels. Conversation flex items no longer shrink vertically.

The existing composer remains in normal flex flow below the scroll area, with aligned 48px input/send controls and a white surface. Send hover/focus and disabled treatment use existing tokens. Draft errors align with the composer; request errors get restrained spacing and a semantic border. Typing dots use a neutral color and a stable-height bubble. Load-older stays centered without changing pagination.

## Citations and intentional limits

The current component renders msg.text as plain text and has no separate citation element. Text and reference data handling were left unchanged; long filename/page text now wraps safely. No citation parsing, separate source block, Markdown renderer, new empty-state copy, or behavioral claim was introduced.

Admin Student Mode inherits the same stylesheet. API calls, request keys, ownership, session state, persistence, scope checks, TTS, and finalized tokens remain untouched.

## Verification

- Frontend suite: 148 passed, 0 failed.
- Lint: passed.
- Production build: passed.
- Responsive CSS reviewed; live desktop/tablet/mobile rendering remains unverified.
