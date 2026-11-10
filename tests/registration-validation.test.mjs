import test from 'node:test';
import assert from 'node:assert/strict';
test('AHRT sections remain valid while section length and character checks are preserved',()=>{
 for(const section of ['AHRT 1A','AHRT 1B','AHRT 2A','A'.repeat(50)]) assert.equal(validateRegistration({...valid,section}).errors.section,undefined);
 for(const section of ['A'.repeat(51),'<script>','']) assert.ok(validateRegistration({...valid,section}).errors.section);
});
import { validateRegistration, registrationFailure } from '../src/utils/registration.js';
const valid={name:" Talidong,  Archie V. ",email:' Archie@PATEROSTECHNOLOGICALCOLLEGE.EDU.PH ',studentId:' 29AHRT-0168 ',section:'AHRT 1A',yearLevel:'FIRST_YEAR',password:'password-123',confirmPassword:'password-123'};
test('registration preserves hyphen and normalizes names/email',()=>{const r=validateRegistration(valid);assert.deepEqual(r.errors,{});assert.equal(r.value.studentId,'29AHRT-0168');assert.equal(r.value.name,'Talidong, Archie V.');});
test('registration rejects malformed IDs',()=>{for(const studentId of ['29BSHM-0168','29ahrt-0168','29AHRT0168','2AHRT-0168','29AHRT-168','202300123','2023-123','23-00123','2023--00123','2023-0012a',''])assert.ok(validateRegistration({...valid,studentId}).errors.studentId);});
test('registration validates each field and confirmation',()=>{const r=validateRegistration({...valid,name:'<script>',email:'a@gmail.com',section:'',yearLevel:'THIRD_YEAR',password:'short',confirmPassword:'other'});assert.equal(Object.keys(r.errors).length,6);});
test('registration accepts legitimate punctuation names and enforces byte limit',()=>{for(const name of ["O'Connor, James",'Anne-Marie Santos','Maria D. Reyes'])assert.equal(validateRegistration({...valid,name}).errors.name,undefined);assert.ok(validateRegistration({...valid,password:'x'.repeat(73)}).errors.password);});
test('registration maps duplicate fields and distinguishes service/network errors',()=>{assert.equal(registrationFailure({response:{status:409,data:{code:'REGISTRATION_VALIDATION_FAILED',errors:{studentId:'Already registered'}}}}).errors.studentId,'Already registered');assert.match(registrationFailure({response:{status:500}}).message,/temporarily/);assert.match(registrationFailure({}).message,/connection/);});
