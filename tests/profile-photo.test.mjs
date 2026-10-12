import { test } from 'node:test';
import assert from 'node:assert/strict';
import { prepareProfilePhoto } from '../src/utils/profilePhoto.js';
test('photo preparation allows JPG PNG WEBP and resizes/reencodes without metadata', async () => {
 const previous = { document: globalThis.document, createImageBitmap: globalThis.createImageBitmap };
 let closed=0, drawn=0;const canvas={getContext:()=>({drawImage(){drawn++;}}),toBlob:callback=>callback(new Blob(['png'],{type:'image/png'}))};
 globalThis.document={createElement:()=>canvas};globalThis.createImageBitmap=async()=>({width:2000,height:1000,close(){closed++;}});
 try {for(const type of ['image/jpeg','image/png','image/webp']) {const result=await prepareProfilePhoto(new Blob(['image'],{type}));assert.equal(result.type,'image/png');assert.equal(canvas.width,512);assert.equal(canvas.height,256);}assert.equal(closed,3);assert.equal(drawn,3);}
 finally {Object.assign(globalThis,previous);}
});
test('unsupported/large/unreadable photos are rejected before upload', async()=>{
 await assert.rejects(prepareProfilePhoto(new Blob(['svg'],{type:'image/svg+xml'})),/JPG, PNG, or WEBP/);
 await assert.rejects(prepareProfilePhoto({type:'image/png',size:6*1024*1024}),/5 MB/);
 const original=globalThis.createImageBitmap;globalThis.createImageBitmap=async()=>{throw new Error('decoder');};
 try{await assert.rejects(prepareProfilePhoto(new Blob(['broken'],{type:'image/webp'})),/could not be read/);}finally{globalThis.createImageBitmap=original;}
});
