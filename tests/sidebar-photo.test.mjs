import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act } from 'react';
let dom,server,createRoot,Router,AuthContext,Sidebar;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const k of ['window','document','HTMLElement','Event','localStorage','sessionStorage'])globalThis[k]=dom.window[k];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));Router=await import('react-router-dom');
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 ({AuthContext}=await server.ssrLoadModule('/src/context/login/useAuth.js'));
 Sidebar=(await server.ssrLoadModule('/src/components/shared/Sidebar.jsx')).default;

});
after(async()=>{await server.close();dom.window.close();});
test('shared actor photo loads, updates, falls back safely and stays the Admin photo in acting modes', async()=>{
 const Provider=(await server.ssrLoadModule('/src/context/login/ProfilePhotoProvider.jsx')).default;
 const {useProfilePhoto}=await server.ssrLoadModule('/src/context/login/useProfilePhoto.js');
 const api=(await server.ssrLoadModule('/src/services/apiClient.js')).default;
 let calls=0;api.defaults.adapter=async config=>{calls++;return {data:new Blob(['photo']),status:200,statusText:'OK',headers:{},config};};
 const oldCreate=URL.createObjectURL,oldRevoke=URL.revokeObjectURL;let urls=0;
 URL.createObjectURL=()=>`blob:photo-${++urls}`;URL.revokeObjectURL=()=>{};
 function Update(){const photo=useProfilePhoto();return React.createElement('button',{id:'save',onClick:()=>photo.updatePhoto(new Blob(['new']))},'Save');}
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);
 const render=(email,path)=>React.createElement(AuthContext.Provider,{value:{user:{name:'Admin',email},token:email}},React.createElement(Provider,null,React.createElement(Router.MemoryRouter,{initialEntries:[path]},React.createElement(Sidebar),React.createElement(Update))));
 try {
  for(const path of ['/admin','/admin/student-preview/target','/admin/student-mode/target','/admin/professor-mode/target']) {
   await act(async()=>root.render(render(path,path)));
   const image=host.querySelector('img[alt="Your profile photo"]');assert.ok(image);const before=image.src;
   await act(async()=>host.querySelector('#save').click());assert.notEqual(image.src,before);
   await act(async()=>image.dispatchEvent(new Event('error')));assert.equal(host.querySelector('img[alt="Your profile photo"]'),null);assert.match(host.textContent,/AdminOnline/);
  }
  assert.equal(calls,4);
  await act(async()=>root.render(render('returning','/student')));assert.ok(host.querySelector('img[alt="Your profile photo"]'));
 } finally {await act(async()=>root.unmount());host.remove();URL.createObjectURL=oldCreate;URL.revokeObjectURL=oldRevoke;}
});

