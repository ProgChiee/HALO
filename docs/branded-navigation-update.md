# Branded Sidebar and Topbar

## Scope
Navigation presentation only. Main content remains #F8FAFC with white cards. Routes, authorization, logout handlers, acting sessions and page content are unchanged.

## Files
- src/components/shared/NavigationBrand.module.css (new navigation-scoped palette)
- src/components/shared/Sidebar.jsx (section labels)
- src/components/shared/Sidebar.module.css
- src/components/shared/Navigation.module.css
- src/components/shared/PageShell.module.css
- src/components/shared/Navbar.module.css
- src/data/navigationData.js
- src/pages/professor/styles/LessonEditor.module.css (header composition only)
- src/pages/student/styles/LessonChat.module.css (header composition only)
- tests/admin-navigation.test.mjs

## Presentation
Sidebar: 180-degree #2F7D5B / #286F51 / #256A4D gradient.
Topbar: 90-degree #2F7D5B / #256A4D gradient.
HALO stays first in the sidebar, followed by a compact role pill when provided. Admin groups are Main (Dashboard, Professor Management, Student Management, Monitoring), Mode Switching (Professor Mode, Student Mode), and Account (Profile).
Other roles retain their existing destinations. Both acting modes inherit shared navigation styling without changes to banners/session logic.
Normal text is 96% white; hover/active surfaces use 2%/4% white to preserve contrast. Calculated contrast against the lightest gradient stop: default 4.74:1, hover 4.80:1, active 4.62:1. Active routes also expose aria-current and a light left border. Keyboard focus is white.
Existing drawer breakpoint remains 1024px. Sidebar children do not shrink on short screens; vertical scrolling keeps logo/navigation/footer reachable. Labels and names wrap. Drawer/menu controls share the branded palette. Desktop legacy Navbar wordmark is hidden to avoid duplicating the sidebar identity.

## Verification
149 frontend tests passed. Includes new desktop/tablet/mobile DOM assertions for logo order, role indicator, group labels, destination order, current route and logout presence. Existing drawer/focus/Escape/logout and acting-mode tests pass.
ESLint and production build pass.
No live browser screenshots were available; width coverage uses the existing JSDOM navigation harness, not a rendered-browser visual check.
