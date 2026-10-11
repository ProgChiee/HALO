import {test,before,after} from 'node:test';
import assert from 'node:assert/strict';
import {youtubeVideoId} from '../src/utils/youtubeVideo.js';
import {JSDOM} from 'jsdom';
import {createServer} from 'vite';
import React,{act} from 'react';
let dom,server,createRoot,Video;
before(async()=>{dom=new JSDOM('<html><body></body></html>',{url:'http://localhost'});for(const key of ['window','document','HTMLElement','Event','KeyboardEvent'])globalThis[key]=dom.window[key];globalThis.IS_REACT_ACT_ENVIRONMENT=true;({createRoot}=await import('react-dom/client'));server=await createServer({server:{middlewareMode:true},appType:'custom'});Video=(await server.ssrLoadModule('/src/components/shared/VideoLearning.jsx')).default;});
after(async()=>{await server.close();dom.window.close();});
test('only supported YouTube hosts and eleven-character video IDs are accepted',()=>{
 for(const url of ['https://www.youtube.com/watch?v=abcdefghijk','https://youtu.be/abcdefghijk','https://www.youtube.com/shorts/abcdefghijk'])assert.equal(youtubeVideoId(url),'abcdefghijk');
 for(const url of ['',null,'bad','https:youtube.com/watch?v=abcdefghijk','https://evil.example/watch?v=abcdefghijk','https://youtube.com.evil.test/watch?v=abcdefghijk','https://youtube.com/watch?v=bad','https://youtube.com/watch?v=abcdefghijk&v=otherother1','https://user@youtube.com/watch?v=abcdefghijk'])assert.equal(youtubeVideoId(url),null);
});
test('valid video opens accessible modal, safe embed, no autoplay; close and target changes unmount player',async()=>{
 const host=document.createElement('div');document.body.append(host);const root=createRoot(host);const render=url=>act(async()=>root.render(React.createElement(Video,{url})));
 try{await render('https://evil.example');assert.equal(host.querySelector('button'),null);await render('https://youtu.be/abcdefghijk');const opener=host.querySelector('button');opener.focus();await act(async()=>opener.click());
 assert.equal(document.querySelector('[role=dialog]').getAttribute('aria-modal'),'true');assert.equal(document.querySelector('iframe').getAttribute('src'),'https://www.youtube.com/embed/abcdefghijk');assert.match(document.body.textContent,/Video unavailable/);
 await act(async()=>document.querySelector('[role="dialog"] button').click());assert.equal(document.querySelector('iframe'),null);assert.equal(document.activeElement,opener);
 await act(async()=>opener.click());await render('https://youtu.be/12345678901');assert.equal(document.querySelector('iframe'),null);
 await act(async()=>host.querySelector('button').click());await act(async()=>document.querySelector('[role=dialog]').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true})));assert.equal(document.querySelector('iframe'),null);
 }finally{await act(async()=>root.unmount());host.remove();}
});
