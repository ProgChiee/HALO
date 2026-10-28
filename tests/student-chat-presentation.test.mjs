import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { mentorAnswerText } from '../src/utils/mentorAnswerText.js';

test('legacy citation footer is presentation-only and preserves the answer', () => {
 const message = Object.freeze({message:'Explain the process step by step. Module sources: lesson.pdf (page 2)',sourceIds:['original-id']});
 assert.equal(mentorAnswerText(message.message),'Explain the process step by step.');
 assert.ok(message.message.includes('Module sources:'));
 assert.deepEqual(message.sourceIds,['original-id']);
 assert.equal(mentorAnswerText('Answer.\nModule sources: Lesson Notes.pdf (page 3); Module PDF.pdf (page 5)'), 'Answer.');
});
test('ordinary learning content and unrecognized references are never broadly stripped', () => {
 for(const text of ['See page 3 for the exercise.', 'Sources of income include room sales.', 'Read lesson.pdf and compare [1] with [2].', 'Module sources: local knowledge and experience.', 'Answer [source 1].', 'Module sources: lesson.pdf (page 2) explains the exercise.']) assert.equal(mentorAnswerText(text),text);
});
test('chat renders only answer text, retains user text, and exposes accessible conversation/composer states', async () => {
 const source=await readFile(new URL('../src/pages/student/components/LessonChat.jsx',import.meta.url),'utf8');
 assert.ok(source.includes("msg.sender === 'ai' ? mentorAnswerText(msg.text) : msg.text"));
 assert.ok(source.includes('speak(msg.id, mentorAnswerText(msg.text))'));
 assert.match(source,/role="log"/);assert.match(source,/aria-label="Message composer"/);assert.match(source,/aria-label="AI Mentor is responding"/);
 assert.doesNotMatch(source,/Sources<|citationFooter|sourcePanel/);
 const css=await readFile(new URL('../src/pages/student/styles/LessonChat.module.css',import.meta.url),'utf8');
 assert.match(css,/prefers-reduced-motion/);assert.match(css,/overscroll-behavior: contain/);assert.match(css,/safe-area-inset-bottom/);
});
