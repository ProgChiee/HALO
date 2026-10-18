import { test } from 'node:test';
import assert from 'node:assert/strict';
import { validateFullName, fullNameErrorMessage } from '../src/utils/fullName.js';
test('full names accept legitimate punctuation and Unicode, trim and normalize spaces',()=>{
 for(const name of ['Talidong, Archie V.','Dela Cruz, Juan P.',"O'Connor, James",'Anne-Marie Santos','Maria D. Reyes','Jos\u00e9 Ni\u00f1o','D\u2019Angelo'])
  assert.deepEqual(validateFullName(name),{value:name,error:''});
 assert.equal(validateFullName('  Talidong,   Archie\tV.  ').value,'Talidong, Archie V.');
 assert.equal(validateFullName('\u00a0Maria\u00a0\u00a0Reyes\u00a0').value,'Maria Reyes');
});
test('full names reject blank, scripting characters, control characters and long values with clear errors',()=>{
 for(const name of ['', ' \t\n ', '\u00a0'])assert.equal(validateFullName(name).error,'Full name is required.');
 assert.match(validateFullName('A'.repeat(101)).error,/100 characters/);
 for(const name of ["<script>alert('x')</script>",'John\\Doe','John; DROP TABLE users','John\u0000Doe','...,---','Name123'])assert.match(validateFullName(name).error,/Use letters/);
 for(const code of ['FULL_NAME_REQUIRED','FULL_NAME_TOO_LONG','INVALID_FULL_NAME'])assert.doesNotMatch(fullNameErrorMessage({response:{status:400,data:{code}}}),/Could not update/);
 assert.doesNotMatch(fullNameErrorMessage({response:{status:500,data:{message:'SQL secret'}}}),/SQL secret/);
});

test('profile errors distinguish API, validation, network, timeout and local failures without leaking details',()=>{
 const api=status=>fullNameErrorMessage({response:{status,data:{message:'SQL secret'}}});
 assert.match(api(500),/server could not save/); assert.doesNotMatch(api(500),/connection|SQL secret/);
 assert.match(api(401),/session has expired/); assert.match(api(403),/permission/);
 for(const status of [404,405]) assert.match(api(status),/unavailable/);
 assert.match(api(400),/rejected/); assert.match(api(422),/rejected/);
 assert.match(fullNameErrorMessage({code:'ERR_NETWORK'}),/Could not reach HALO/);
 assert.match(fullNameErrorMessage({code:'ECONNABORTED'}),/timed out/);
 assert.match(fullNameErrorMessage({code:'PROFILE_ACCOUNT_CHANGED'}),/account changed/);
 assert.doesNotMatch(fullNameErrorMessage(new Error('SQL secret')),/connection|SQL secret/);
});
