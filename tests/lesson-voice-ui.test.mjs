import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let server, dom, createRoot, Chat, AuthContext, ToastContext, Workflow, MemoryRouter, Routes, Route, useNavigate, navigate;
function NavigationProbe() { navigate = useNavigate(); return null; }
class Recognition {
  static instances = [];
  constructor() { Recognition.instances.push(this); }
  start() { this.onstart?.(); }
  stop() { this.stopped = true; }
  abort() { this.aborted = true; }
}
before(async () => {
  dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
  for (const key of ['window', 'document', 'HTMLElement', 'Event', 'MouseEvent', 'localStorage', 'sessionStorage']) globalThis[key] = dom.window[key];
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  dom.window.HTMLElement.prototype.scrollIntoView = () => {};
  ({ createRoot } = await import('react-dom/client'));
  ({ MemoryRouter, Routes, Route, useNavigate } = await import('react-router-dom'));
  server = await createServer({ server: { middlewareMode: true, hmr: false }, appType: 'custom' });
  Chat = (await server.ssrLoadModule('/src/pages/student/components/LessonChat.jsx')).default;
  ({ AuthContext } = await server.ssrLoadModule('/src/context/login/useAuth.js'));
  ({ ToastContext } = await server.ssrLoadModule('/src/context/notifications/useToast.js'));
  ({ StudentWorkflowContext: Workflow } = await server.ssrLoadModule('/src/context/student/useStudentWorkflow.js'));
});
after(async () => { await server.close(); dom.window.close(); });

for (const mode of ['student', 'preview', 'support']) test(`${mode}: dictation only edits the composer; explicit Send uses existing workflow`, async () => {
  window.SpeechRecognition = Recognition;
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  const sends = [], opens = [];
  const api = {
    getWeekLesson: async week => ({ id: Number(week), subjectId: 1, weekId: Number(week), weekNumber: 1, status: 'APPROVED', aiGenerationStatus: 'COMPLETED', files: [] }),
    openSession: async id => { opens.push(id); return { moduleId: id, sessionId: 1, messages: [] }; },
    recordLessonStudy: async () => {},
    sendMessage: async (...args) => { sends.push(args); return { haloMessage: 'Answer' }; },
  };
  const render = (identity = mode, week = '2', email = 'student@example.test') => root.render(React.createElement(MemoryRouter, { initialEntries: ['/lesson/1/2'] },
    React.createElement(NavigationProbe), React.createElement(AuthContext.Provider, { value: { user: { email, name: 'Student' }, role: 'student', logout() {} } },
      React.createElement(ToastContext.Provider, { value: { showToast() {} } },
        React.createElement(Workflow.Provider, { value: { api, identity, preview: mode === 'preview', acting: mode !== 'student', target: { name: 'Target' }, leave() {}, basePath: '/student' } },
          React.createElement(Routes, null, React.createElement(Route, { path: '/lesson/:topicId/:weekId', element: React.createElement(Chat, { key: week }) })))))));
  const mic = () => host.querySelector('button[title="Use voice input"]');
  const input = () => host.querySelector('[aria-label="Message AI Mentor"]');
  try {
    await act(async () => render());
    assert.ok(mic()); assert.equal(mic().type, 'button'); assert.equal(host.querySelector('[aria-label="Voice language"]').value, 'en-US');
    const existingOpens = opens.length;
    await act(async () => { const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set; setter.call(input(), 'Typed'); input().dispatchEvent(new Event('input', { bubbles: true })); });
    await act(async () => mic().click()); const r = Recognition.instances.at(-1);
    assert.match(host.textContent, /Listening/); assert.equal(opens.length, existingOpens); assert.equal(sends.length, 0);
    await act(async () => r.onresult({ results: [Object.assign([{ transcript: 'spoken question' }], { isFinal: true })] }));
    assert.equal(input().value, 'Typed spoken question'); assert.equal(sends.length, 0);
    assert.equal(host.querySelector('[aria-label="Send message"]').disabled, true);
    await act(async () => r.onend());
    await act(async () => host.querySelector('[aria-label="Send message"]').click());
    assert.equal(sends.length, 1); assert.equal(sends[0][1], 'Typed spoken question');
    await act(async () => mic().click()); const stale = Recognition.instances.at(-1), callback = stale.onresult;
    await act(async () => render(`${mode}:new-target`)); assert.equal(stale.aborted, true);
    await act(async () => callback({ results: [Object.assign([{ transcript: 'stale' }], { isFinal: true })] }));
    assert.equal(input().value, '');
    await act(async () => mic().click()); const oldAccount = Recognition.instances.at(-1);
    await act(async () => render(`${mode}:new-target`, '2', 'another@example.test')); assert.equal(oldAccount.aborted, true);
    await act(async () => mic().click()); const previousModule = Recognition.instances.at(-1), previousResult = previousModule.onresult;
    await act(async () => navigate('/lesson/1/3')); assert.equal(previousModule.aborted, true);
    await act(async () => previousResult({ results: [Object.assign([{ transcript: 'old module' }], { isFinal: true })] }));
    assert.equal(input().value, '');
    await act(async () => mic().click()); const leaving = Recognition.instances.at(-1);
    await act(async () => window.dispatchEvent(new Event('pagehide'))); assert.equal(leaving.aborted, true);
    await act(async () => mic().click()); const unmounting = Recognition.instances.at(-1);
    await act(async () => root.unmount()); assert.equal(unmounting.aborted, true);
  } finally { await act(async () => root.unmount()); host.remove(); }
});

test('unsupported browser keeps typed chat usable', async () => {
  delete window.SpeechRecognition;
  const Component = (await server.ssrLoadModule('/src/pages/student/components/LessonVoiceInput.jsx')).default;
  const host = document.createElement('div'), root = createRoot(host);
  await act(async () => root.render(React.createElement(Component, { onFinal() {}, onBusyChange() {}, onStart() {} })));
  assert.equal(host.querySelector('button').disabled, true); assert.match(host.textContent, /not supported/);
  await act(async () => root.unmount());
});
test('language selector, keyboard focus, Stop and permission feedback are accessible', async () => {
  window.SpeechRecognition = Recognition;
  const Component = (await server.ssrLoadModule('/src/pages/student/components/LessonVoiceInput.jsx')).default;
  const host = document.createElement('div'); document.body.append(host); const root = createRoot(host);
  try {
    await act(async () => root.render(React.createElement(Component, { onFinal() {}, onBusyChange() {}, onStart() {} })));
    assert.equal(host.querySelector('#voice-input-help').textContent, 'Review your transcription before sending.');
    const privacy = host.querySelector('[aria-label="Voice input privacy information"]');
    privacy.focus(); assert.equal(document.activeElement, privacy);
    assert.equal(privacy.type, 'button');
    assert.equal(privacy.getAttribute('aria-describedby'), 'voice-privacy-help');
    assert.match(host.querySelector('[role="tooltip"]').textContent, /browser’s speech service/);
    const select = host.querySelector('select');
    await act(async () => { select.value = 'fil-PH'; select.dispatchEvent(new Event('change', { bubbles: true })); });
    const button = host.querySelector('button'); button.focus(); assert.equal(document.activeElement, button);
    await act(async () => button.click()); const r = Recognition.instances.at(-1);
    assert.equal(r.lang, 'fil-PH'); assert.equal(button.getAttribute('aria-label'), 'Stop voice input');
    await act(async () => button.click()); assert.equal(r.stopped, true); assert.match(host.textContent, /Transcribing/);
    await act(async () => r.onerror({ error: 'not-allowed' }));
    assert.match(host.querySelector('[role="alert"]').textContent, /permission/); assert.equal(button.disabled, false);
  } finally { await act(async () => root.unmount()); host.remove(); }
});
