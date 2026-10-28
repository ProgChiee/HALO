import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
test('quiz uses compact responsive theme surfaces and readable selected answers',async()=>{
 const css=await readFile(new URL('../src/pages/student/styles/Quiz.module.css',import.meta.url),'utf8');
 assert.match(css,/background: var\(--color-surface\)/);assert.match(css,/min-height: 48px/);assert.match(css,/overflow-wrap: anywhere/);assert.match(css,/box-shadow: inset 3px/);assert.match(css,/@media \(max-width: 480px\)/);assert.match(css,/:focus-visible/);
 const source=await readFile(new URL('../src/pages/student/components/Quiz.jsx',import.meta.url),'utf8');
 assert.match(source,/role="group" aria-labelledby="quiz-question"/);assert.match(source,/role="progressbar"/);assert.match(source,/result.passed \? 'Passed' : 'Not passed'/);assert.doesNotMatch(source,/Â·|â€”/);
});
