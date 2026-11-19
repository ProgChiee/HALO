import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
import { createServer as createHttpServer } from 'node:http';
const NativeFormData=globalThis.FormData;
let writeSession;
let dom,server,createRoot,Router,Registry,AuthContext,api;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event','MouseEvent','localStorage','sessionStorage','FormData'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;window.matchMedia=()=>({matches:false,addEventListener(){},removeEventListener(){}});
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true,hmr:false},appType:'custom'});
 Registry=(await server.ssrLoadModule('/src/pages/admin/components/StudentRegistry.jsx')).default;
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 ({writeSession}=await server.ssrLoadModule('/src/utils/session.js'));
});
after(async()=>{await server?.close();dom?.window.close();});
test('registry previews without saving, requires confirmation, supports cancel and server filters',async()=>{
 const calls=[];const sample={total:2,eligible:1,ineligible:1,invalid:0,duplicates:0,rows:[{row:2,studentId:'23AHRT-0168',fullName:'Synthetic Student',email:'synthetic@example.test',course:'AHRT',status:'ELIGIBLE',errors:[]}]};
 api.defaults.adapter=async config=>{calls.push(config);let data=config.url.endsWith('/history')?[]:config.method==='post'?sample:{content:[],totalElements:0};return {data,status:200,headers:{},config};};
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
 const click=async b=>act(async()=>b.dispatchEvent(new MouseEvent('click',{bubbles:true})));
 try{
  await act(async()=>root.render(React.createElement(AuthContext.Provider,{value:{user:{name:'Admin'}}},React.createElement(Router.MemoryRouter,null,React.createElement(Registry)))));
  await act(async()=>new Promise(r=>setTimeout(r,250)));assert.match(host.textContent,/No registry records found/);
  const input=host.querySelector('[type=file]');Object.defineProperty(input,'files',{value:[new window.File(['synthetic'],'registry.xlsx')]});await act(async()=>input.dispatchEvent(new Event('change',{bubbles:true})));
  await click(button('Preview import'));assert.equal(calls.filter(c=>c.url.endsWith('/confirm')).length,0);assert.match(host.textContent,/1 eligible, 1 ineligible/);
  await click(button('Import 1 eligible rows'));assert.ok(document.querySelector('[role=dialog]'));await click(button('Cancel'));assert.equal(calls.filter(c=>c.url.endsWith('/confirm')).length,0);
  await click(button('Import 1 eligible rows'));await click(button('Confirm import'));assert.equal(calls.filter(c=>c.url.endsWith('/confirm')).length,1);assert.match(host.textContent,/1 records imported; 1 skipped/);
  const search=host.querySelector('[placeholder="Student ID, name or email"]');await act(async()=>{Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype,'value').set.call(search,'23AHRT');search.dispatchEvent(new Event('input',{bubbles:true}));});await act(async()=>new Promise(r=>setTimeout(r,250)));
  assert.ok(calls.some(c=>c.params?.search==='23AHRT'&&c.params.page===0));
 }finally{await act(async()=>root.unmount());host.remove();}
});

test('multipart transport attaches the stored Admin bearer and generates a matching boundary',async()=>{
 const received=[];
 const local=createHttpServer(async(req,res)=>{const chunks=[];for await(const chunk of req)chunks.push(chunk);received.push({method:req.method,url:req.url,headers:req.headers,body:Buffer.concat(chunks).toString()});res.setHeader('Content-Type','application/json');res.end('{"eligible":1}');});
 await new Promise(resolve=>local.listen(0,'127.0.0.1',resolve));
 const originalFormData=globalThis.FormData;globalThis.FormData=NativeFormData;
 try{
  writeSession({name:'Synthetic Admin',email:'admin@example.test',role:'ADMIN'},'synthetic-test-token');
  const form=new NativeFormData();form.append('file',new Blob(['synthetic workbook']),'fixture.xlsx');
  const baseURL='http://127.0.0.1:'+local.address().port+'/api';
  await api.post('/admin/student-registry/preview',form,{baseURL,adapter:'fetch'});
  await api.post('/admin/create-professor',{name:'Synthetic'},{baseURL,adapter:'fetch'});
  assert.equal(received[0].method,'POST');assert.equal(received[0].url,'/api/admin/student-registry/preview');
  assert.equal(received[0].headers.authorization,'Bearer synthetic-test-token');assert.equal(received[0].headers.authorization,received[1].headers.authorization);
  const type=received[0].headers['content-type'];assert.match(type,/^multipart\/form-data; boundary=/);
  const boundary=type.split('boundary=')[1];assert.ok(received[0].body.includes('--'+boundary));assert.ok(received[0].body.includes('name="file"; filename="fixture.xlsx"'));
 }finally{localStorage.clear();sessionStorage.clear();globalThis.FormData=originalFormData;local.closeAllConnections();await new Promise(resolve=>local.close(resolve));}
});
