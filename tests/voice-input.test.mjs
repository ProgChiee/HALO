import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createVoiceInput, appendTranscript, speechRecognitionConstructor } from '../src/utils/voiceInput.js';

class Recognition {
  static instances = [];
  constructor() { Recognition.instances.push(this); }
  start() { this.starts = (this.starts || 0) + 1; this.onstart?.(); }
  stop() { this.stops = (this.stops || 0) + 1; }
  abort() { this.aborts = (this.aborts || 0) + 1; }
}
const result = (text, isFinal) => Object.assign([{ transcript: text }], { isFinal });
function fixture(Type = Recognition) {
  const state = { draft: 'Typed question', interim: '', status: '', error: '' };
  const voice = createVoiceInput({ Recognition: Type, onState: s => state.status = s,
    onInterim: s => state.interim = s, onError: s => state.error = s,
    onFinal: s => state.draft = appendTranscript(state.draft, s) });
  return { voice, state, get recognition() { return Recognition.instances.at(-1); } };
}
test('feature detection supports standard, prefixed, and unsupported browsers', () => {
  assert.equal(speechRecognitionConstructor({ SpeechRecognition: Recognition }), Recognition);
  assert.equal(speechRecognitionConstructor({ webkitSpeechRecognition: Recognition }), Recognition);
  assert.equal(speechRecognitionConstructor({}), undefined);
});
test('starts only on demand, preserves edits, distinguishes interim/final and deduplicates result indexes', () => {
  const count = Recognition.instances.length, f = fixture();
  assert.equal(Recognition.instances.length, count);
  f.voice.start('fil-PH'); const r = f.recognition;
  assert.equal(r.lang, 'fil-PH'); assert.equal(r.continuous, false); assert.equal(f.state.status, 'listening');
  f.voice.start('en-US'); assert.equal(Recognition.instances.length, count + 1);
  r.onresult({ results: [result('hello', false)] });
  assert.equal(f.state.draft, 'Typed question'); assert.equal(f.state.interim, 'hello');
  f.state.draft = 'Edited while listening';
  r.onresult({ results: [result('hello', true), result('world', false)] });
  r.onresult({ results: [result('hello', true), result('world', true)] });
  r.onresult({ results: [result('hello', true), result('world', true)] });
  assert.equal(f.state.draft, 'Edited while listening hello world');
  r.onend(); assert.equal(f.state.status, 'idle'); assert.equal(f.state.interim, '');
  f.voice.dispose();
});
test('stop is idempotent, waits for a final result, then permits another session', () => {
  const f = fixture(); f.voice.start('en-US'); const r = f.recognition;
  f.voice.stop(); f.voice.stop(); assert.equal(r.stops, 1); assert.equal(f.state.status, 'processing');
  r.onresult({ results: [result('final', true)] }); r.onend();
  f.voice.start('en-US'); assert.notEqual(f.recognition, r); f.voice.dispose();
});
for (const [code, message] of [['not-allowed', /permission/], ['no-speech', /No speech/], ['audio-capture', /microphone/], ['network', /connect/], ['service-not-allowed', /service/], ['language-not-supported', /selected speech language/]]) {
  test(`recognition error ${code} preserves text and permits retry`, () => {
    const f = fixture(); f.voice.start('en-US'); f.recognition.onerror({ error: code });
    assert.match(f.state.error, message); assert.equal(f.state.draft, 'Typed question'); assert.equal(f.state.status, 'idle');
    f.voice.start('en-US'); assert.equal(f.state.error, ''); f.voice.dispose();
  });
}
test('cancel/dispose reject captured stale callbacks and detach browser handlers', () => {
  const f = fixture(); f.voice.start('en-US'); const r = f.recognition, stale = r.onresult;
  f.voice.cancel(); stale({ results: [result('wrong module', true)] });
  assert.equal(r.aborts, 1); assert.equal(r.onresult, null); assert.equal(f.state.draft, 'Typed question');
  f.voice.start('en-US'); const next = f.recognition, late = next.onresult;
  f.voice.dispose(); late({ results: [result('wrong student', true)] });
  assert.equal(next.aborts, 1); assert.equal(f.state.draft, 'Typed question');
});
test('browser cancellation is quiet and empty recognition gives useful feedback', () => {
  const f = fixture(); f.voice.start('en-US'); f.recognition.onerror({ error: 'aborted' }); assert.equal(f.state.error, '');
  f.voice.start('en-US'); f.recognition.onend(); assert.match(f.state.error, /No speech/); f.voice.dispose();
});
test('synchronous start failure leaves the draft intact', () => {
  class Broken extends Recognition { start() { throw Object.assign(new Error(), { name: 'NotAllowedError' }); } }
  const f = fixture(Broken); f.voice.start('en-US'); assert.match(f.state.error, /permission/); assert.equal(f.state.status, 'idle'); f.voice.dispose();
});
test('processing watchdog aborts a stuck recognizer and preserves completed text', t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const f = fixture(); f.voice.start('en-US'); const r = f.recognition;
  r.onresult({ results: [result('saved words', true)] });
  f.voice.stop(); t.mock.timers.tick(8000);
  assert.equal(r.aborts, 1); assert.equal(f.state.status, 'idle');
  assert.equal(f.state.draft, 'Typed question saved words'); assert.match(f.state.error, /too long/);
  f.voice.dispose();
});
