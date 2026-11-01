import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { JSDOM } from 'jsdom';
import { readFileSync } from 'node:fs';
let server, Item;
before(async()=>{server=await createServer({server:{middlewareMode:true},appType:'custom'});Item=(await server.ssrLoadModule('/src/pages/superadmin/components/RecentActivityItem.jsx')).default;});
after(async()=>await server.close());
function render(item){return new JSDOM(renderToStaticMarkup(React.createElement(Item,{item})));}
test('friendly mode and auth summaries retain actor attribution without technical audit text',()=>{
 for(const title of ['Entered Professor Mode','Exited Professor Mode','Entered Student Mode','Exited Student Mode','Entered Admin Mode','Exited Admin Mode','Entered Professor Preview','Exited Student Preview','Signed in','Signed out','Failed sign-in attempt']){
 const dom=render({activityTitle:title,activityDescription:'Alex recorded this activity for Jordan.',activityKind:'mode',action:'Super Admin 4 acting as PROFESSOR 21',createdAt:'2026-11-02T09:30:00'});
 assert.ok(dom.window.document.body.textContent.includes(title));assert.match(dom.window.document.body.textContent,/Alex/);assert.doesNotMatch(dom.window.document.body.textContent,/PROFESSOR 21|Super Admin 4/);assert.equal(dom.window.document.querySelector('svg').parentElement.getAttribute('aria-hidden'),'true');dom.window.close();
 }
});
test('legacy API fallback never renders raw technical action or email',()=>{const dom=render({userName:'Alex',action:'private@example.test STUDENT 21',displayAction:'raw audit',createdAt:'invalid'});assert.match(dom.window.document.body.textContent,/Recorded activity/);assert.match(dom.window.document.body.textContent,/Time unavailable/);assert.doesNotMatch(dom.window.document.body.textContent,/@|STUDENT|raw audit/);dom.window.close();});
test('timestamp preserves original value and uses readable local formatting',()=>{const value='2026-11-02T09:30:00';const dom=render({createdAt:value});const time=dom.window.document.querySelector('time');assert.equal(time.getAttribute('datetime'),value);assert.equal(time.textContent,new Date(value).toLocaleString(undefined,{year:'numeric',month:'short',day:'numeric',hour:'numeric',minute:'2-digit'}));dom.window.close();});
test('compact responsive list uses theme tokens and keeps existing API pagination',()=>{const css=readFileSync('src/pages/superadmin/styles/SuperAdminDashboard.module.css','utf8');assert.match(css,/minmax\(0, 1fr\)/);assert.match(css,/@media \(max-width: 768px\)/);assert.match(css,/overflow-wrap: anywhere/);assert.match(css,/var\(--color-text-muted\)/);const page=readFileSync('src/pages/superadmin/components/SuperAdminDashboard.jsx','utf8');assert.match(page,/<ul className=\{styles.activityList\}/);assert.match(page,/slice\(0, 10\)/);const api=readFileSync('src/services/superadmin/superadminService.js','utf8');assert.match(api,/page: 0, size: 10/);});
