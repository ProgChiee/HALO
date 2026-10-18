import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'vite';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
test('quiz version identifies preserved unfinished attempts without mislabeling new or completed attempts',async()=>{
 const server=await createServer({server:{middlewareMode:true},appType:'custom'});
 try{
  const Component=(await server.ssrLoadModule('/src/pages/student/components/QuizVersion.jsx')).default;
  const render=props=>renderToStaticMarkup(React.createElement(Component,props));
  const resumed=render({version:1,currentVersion:4,continuing:true});assert.match(resumed,/Quiz Version 1/);assert.match(resumed,/unfinished Version 1 attempt/);assert.match(resumed,/New attempts use Version 4/);
  const current=render({version:4,currentVersion:4,continuing:false});assert.match(current,/Quiz Version 4/);assert.doesNotMatch(current,/unfinished/);
  assert.doesNotMatch(render({version:1,currentVersion:4,continuing:false}),/unfinished/);
  assert.equal(render({}), '');
 }finally{await server.close();}
});
