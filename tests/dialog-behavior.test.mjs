import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import { createServer } from 'vite';
import React, { act, useState } from 'react';
let dom, server, createRoot, Dialog;
before(async()=>{
 dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});
 for(const key of ['window','document','HTMLElement','Event'])globalThis[key]=dom.window[key];
 globalThis.IS_REACT_ACT_ENVIRONMENT=true;
 ({createRoot}=await import('react-dom/client'));
 server=await createServer({server:{middlewareMode:true},appType:'custom'});
 Dialog=(await server.ssrLoadModule('/src/components/shared/Dialog.jsx')).default;
});
after(async()=>{await server?.close();dom?.window.close();});
const key=async(node,key,shiftKey=false)=>act(async()=>node.dispatchEvent(new window.KeyboardEvent('keydown',{key,shiftKey,bubbles:true,cancelable:true})));
const click=async node=>act(async()=>node.click());
const h=React.createElement;
async function mount(options={}){
 const host=document.createElement('div');document.body.append(host);let actions=0;
 const root=createRoot(host);
 function App(){
  const [open,setOpen]=useState(false),[nested,setNested]=useState(false),[busy,setBusy]=useState(false);
  return h(React.Fragment,null,h('button',{onClick:()=>actions++},'Background action'),h('button',{onClick:e=>{e.currentTarget.focus();setOpen(true);}},'Open'),open&&h(Dialog,{labelledBy:'outer-title',closeOnEscape:options.escape,busy,onClose:()=>setOpen(false)},
   h('h2',{id:'outer-title'},'Outer'),h('input',{'aria-label':'Draft',defaultValue:'unsaved'}),
   h('button',{style:{display:'none'},onClick:()=>{}},'Hidden'),
   h('button',{onClick:()=>setNested(true)},'Nested'),
   h('button',{onClick:()=>setBusy(true)},'Submit'),h('button',{onClick:()=>setOpen(false),disabled:busy},'Cancel'),
   nested&&h(Dialog,{labelledBy:'inner-title',closeOnEscape:true,onClose:()=>setNested(false),initialFocus:'button'},h('h2',{id:'inner-title'},'Inner'),h('button',{onClick:()=>setNested(false)},'Close inner'))));
 }
 await act(async()=>root.render(h(App)));
 const opener=[...host.querySelectorAll('button')].find(b=>b.textContent==='Open');await click(opener);
 return {host,opener,get actions(){return actions;},async close(){await act(async()=>root.unmount());host.remove();}};
}
test('outside pointer and keyboard actions cannot dismiss or activate background; safe explicit Cancel restores state',async()=>{
 const ui=await mount();try{
  const dialog=document.querySelector('[role=dialog]');assert.equal(document.activeElement.getAttribute('aria-label'),'Draft');
  assert.equal(ui.host.hasAttribute('inert'),true);assert.equal(document.body.style.overflow,'hidden');
  await click(dialog.parentElement);await click(ui.host.querySelector('button'));
  for(const type of ['pointerdown','mousedown','touchstart']){const event=new Event(type,{bubbles:true,cancelable:true});ui.host.querySelector('button').dispatchEvent(event);assert.equal(event.defaultPrevented,true);}
  assert.equal(ui.actions,0);assert.ok(dialog.isConnected);
  await key(dialog,'Escape');assert.ok(dialog.isConnected);assert.equal(dialog.querySelector('input').value,'unsaved');
  await act(async()=>ui.host.querySelector('button').focus());assert.ok(dialog.contains(document.activeElement));
  const buttons=[...dialog.querySelectorAll('button')].filter(b=>b.style.display!=='none');
  await act(async()=>buttons.at(-1).focus());await key(document.activeElement,'Tab');assert.equal(document.activeElement,dialog.querySelector('input'));
  await key(document.activeElement,'Tab',true);assert.equal(document.activeElement,buttons.at(-1));
  await click(buttons.at(-1));assert.equal(document.activeElement,ui.opener);assert.equal(ui.host.hasAttribute('inert'),false);assert.equal(document.body.style.overflow,'');
 }finally{await ui.close();}
});
test('only topmost nested dialog is interactive; closing nested restores outer focus and scroll lock',async()=>{
 const ui=await mount();try{
  const outer=document.querySelector('[role=dialog]');const trigger=[...outer.querySelectorAll('button')].find(b=>b.textContent==='Nested');trigger.focus();await click(trigger);
  const inner=[...document.querySelectorAll('[role=dialog]')].at(-1);
  assert.ok(outer.closest('[inert]'));assert.equal(inner.closest('[inert]'),null);
  await click(outer.querySelector('button'));assert.ok(inner.isConnected);
  await key(inner,'Tab');assert.ok(inner.contains(document.activeElement));
  await key(inner,'Escape');assert.equal(document.querySelectorAll('[role=dialog]').length,1);assert.equal(document.activeElement,trigger);assert.equal(outer.closest('[inert]'),null);assert.equal(document.body.style.overflow,'hidden');
  await click([...outer.querySelectorAll('button')].find(b=>b.textContent==='Cancel'));assert.equal(document.activeElement,ui.opener);
 }finally{await ui.close();}
});
test('Escape is opt-in for information and remains blocked during submission',async()=>{
 const ui=await mount({escape:true});try{
  let dialog=document.querySelector('[role=dialog]');await key(dialog,'Escape');assert.equal(document.querySelector('[role=dialog]'),null);assert.equal(document.activeElement,ui.opener);
  await click(ui.opener);dialog=document.querySelector('[role=dialog]');await click([...dialog.querySelectorAll('button')].find(b=>b.textContent==='Submit'));
  await key(dialog,'Escape');await click(dialog.parentElement);assert.ok(dialog.isConnected);assert.equal(dialog.getAttribute('aria-busy'),'true');
 }finally{await ui.close();}
});
test('dynamic background nodes remain inert and pre-existing inert and scroll styles are restored',async()=>{
 const prior=document.createElement('div');prior.setAttribute('inert','');document.body.append(prior);document.body.style.overflow='clip';
 const ui=await mount();const extra=document.createElement('button');try{
  await act(async()=>document.body.append(extra));assert.ok(extra.hasAttribute('inert'));
  await click([...document.querySelectorAll('[role=dialog] button')].find(b=>b.textContent==='Cancel'));
  assert.equal(extra.hasAttribute('inert'),false);assert.ok(prior.hasAttribute('inert'));assert.equal(document.body.style.overflow,'clip');
 }finally{await ui.close();prior.remove();extra.remove();document.body.style.overflow='';}
});
