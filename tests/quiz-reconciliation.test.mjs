import { test } from 'node:test';
import assert from 'node:assert/strict';
import { shouldReconcileQuiz, isSavedQuizResult } from '../src/utils/quizReconciliation.js';
test('quiz recovery distinguishes uncertain failures from clear rejection', () => {
 for (const code of ['ECONNABORTED','ETIMEDOUT','ERR_NETWORK']) assert.equal(shouldReconcileQuiz({code}), true);
 assert.equal(shouldReconcileQuiz({response:{status:409,data:{code:'ASSESSMENT_ALREADY_SUBMITTED'}}}),true);
 for (const status of [400,401,403,404,409,500]) assert.equal(shouldReconcileQuiz({response:{status,data:{code:'VALIDATION_ERROR'}}}),false);
 assert.equal(shouldReconcileQuiz({code:'ERR_CANCELED'}),false);
 assert.equal(shouldReconcileQuiz(new Error('local error')),false);
 assert.equal(isSavedQuizResult({attemptId:1,score:100,passed:true},1),true);
 assert.equal(isSavedQuizResult({attemptId:2,score:100,passed:true},1),false);
});
