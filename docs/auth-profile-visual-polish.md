# Auth and Profile visual polish

## Files changed

- `src/pages/login/styles/Login.module.css`
- `src/pages/login/styles/ForgotPassword.module.css`
- `src/pages/admin/styles/AdminProfile.module.css`
- `src/pages/professor/styles/ProfessorProfile.module.css`
- `src/pages/student/styles/Profile.module.css`
- `src/components/shared/ChangePasswordModal.module.css`

## Findings and improvements

Login/reset wrappers and form cards both had borders and shadows. The outer wrapper is now visually neutral, leaving a single light form surface. Titles use the existing 24px-equivalent token; mobile padding is controlled. Password inputs reserve room for visibility controls, whose alignment and hover treatment are consistent. Long supporting/error text wraps safely. Login secondary controls can wrap.

Admin/Professor/Student profiles now use 48px avatars, quieter labels/role metadata, readable wrapped names and emails, consistent identity spacing, and smaller account headings. Password modal header/close controls and mobile actions accommodate long text. Existing shared error primitives remain in use.

## Scope and preserved behavior

Forgot Password stylesheet covers the existing email/OTP/reset stages. SMTP, OTP expiry/generation, validation, autocomplete, focus handling, password visibility logic, sessions/JWTs, APIs, routes, and permission behavior are unchanged. Acting profile pages inherit these styles and remain read-only with credential controls hidden. Shared password-modal consumers inherit compatible visual changes. Registration and unrelated pages were intentionally not changed. No finalized design tokens changed.

## Results

- Frontend suite: 148 passed, 0 failed.
- Lint: passed.
- Production build: passed.
- Responsive CSS reviewed; live desktop/tablet/mobile browser appearance remains unverified.
