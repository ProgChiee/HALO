// Uses only the reserved synthetic 9002 account in the disposable sandbox.
import { readFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
const root=process.argv[2];
await readFile(`${root}/HALO-DISPOSABLE-SANDBOX`);
await readFile(`${root}/ready`);
const access=await readFile(`${root}/admin-access.txt`,'utf8');
const password=randomUUID()+'!Aa1';
const email='halo.sandbox.9002@paterostechnologicalcollege.edu.ph';
const student={name:'Sandbox Student Two',studentId:'29AHRT-9002',email,password,section:'AHRT 1A',yearLevel:'FIRST_YEAR'};
async function post(path,body){return fetch('http://127.0.0.1:8182/api/auth/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(15000)});}
assert.equal((await post('register/student',student)).status,400);
assert.equal((await post('register/student/code',{studentId:student.studentId,email})).status,200);
let code;
for(let i=0;i<20&&!code;i++){
 const messages=await (await fetch('http://127.0.0.1:18025/messages')).json();
 const message=messages.find(m=>m.includes(email));code=message?.match(/registration code is: (\d{6})/)?.[1];
 if(!code)await new Promise(resolve=>setTimeout(resolve,250));
}
assert.ok(code,'Registration email must arrive in local catcher');
assert.equal((await post('register/student',{...student,verificationCode:code})).status,200);
const login=await post('login',{email,password});assert.equal(login.status,200);
const loggedIn=await login.json();assert.equal(loggedIn.role,'STUDENT');assert.ok(loggedIn.token);
const duplicate=await post('register/student',{...student,verificationCode:code});assert.ok([400,409].includes(duplicate.status));
const admin=await post('login',{email:'sandbox-admin@example.test',password:access.match(/Password: (.+)/)[1]});assert.equal(admin.status,200);
const token=(await admin.json()).token;
const result=await fetch('http://127.0.0.1:8182/api/admin/student-registry?search=29AHRT-9002',{headers:{Authorization:`Bearer ${token}`}});
assert.equal(result.status,200);const records=await result.json();
assert.equal(records.totalElements,1);assert.equal(records.content[0].registrationStatus,'REGISTERED');
console.log('PASS: unverified rejection, local SMTP capture, verified sign-up, student login, duplicate rejection, Admin REGISTERED status. No codes or credentials printed.');
