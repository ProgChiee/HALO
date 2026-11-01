import { test } from 'node:test';import assert from 'node:assert/strict';import { mkdtemp,writeFile,rm } from 'node:fs/promises';import { tmpdir } from 'node:os';import path from 'node:path';import {createServer} from 'node:http';
import {objectKey,planMigration,applyMigration,BUCKET} from '../scripts/migrate-learning-materials.mjs';
async function fixture(){const root=await mkdtemp(path.join(tmpdir(),'halo-material-migration-'));await writeFile(path.join(root,'abc'),'%PDF-synthetic');return{root,clean:async()=>{assert.ok(root.startsWith(path.join(tmpdir(),'halo-material-migration-')));await rm(root,{recursive:true});}};}
const inventory=()=>({attachments:[{filePath:'abc',fileType:'application/pdf'}],revisions:[],history:[{payload:JSON.stringify({files:[{filePath:'uploads/lessons/abc',fileType:'application/pdf'}]})}]});
test('offline dry-run includes historical references, deduplicates keys and calculates checksums',async()=>{const f=await fixture();try{const plan=await planMigration(f.root,inventory());assert.equal(plan.referenceCount,2);assert.equal(plan.entries.length,1);assert.equal(plan.historyCount,1);assert.match(plan.entries[0].sha256,/^[a-f0-9]{64}$/);assert.equal(plan.entries[0].size,14);}finally{await f.clean();}});
test('legacy root mapping rejects traversal, URLs and unmapped absolute paths',()=>{assert.equal(objectKey('C:\\old\\lessons\\abc','C:/old/lessons'),'abc');for(const value of ['../secret','uploads/lessons/../../secret','C:/other/abc','https://example.test/file','x%2fy','a//b'])assert.throws(()=>objectKey(value,'C:/old/lessons'));});
test('incomplete inventory and missing historical files block migration',async()=>{const f=await fixture();try{await assert.rejects(()=>planMigration(f.root,{attachments:[]}));const data=inventory();data.history.push({payload:{files:[{filePath:'missing',fileType:'application/pdf'}]}});await assert.rejects(()=>planMigration(f.root,data));}finally{await f.clean();}});
test('private remote copy verifies checksums and resumes without overwriting existing objects',async()=>{
 const f=await fixture(),objects=new Map();let uploads=0,publicBucket=false;const server=createServer(async(req,res)=>{
  assert.equal(req.headers.apikey,'sb_secret_test');assert.equal(req.headers.authorization,undefined);
  if(req.url.includes('/bucket/')){res.end(JSON.stringify({public:publicBucket}));return;}
  const key=req.url.split('/').at(-1);
  if(req.method==='GET'){if(!objects.has(key)){res.writeHead(404);res.end();}else res.end(objects.get(key));}
  else if(req.method==='POST'){assert.equal(req.headers['x-upsert'],'false');const chunks=[];for await(const chunk of req)chunks.push(chunk);uploads++;objects.set(key,Buffer.concat(chunks));res.end('{}');}
  else assert.fail('Migration must never delete or overwrite');
 });await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 try{const plan=await planMigration(f.root,inventory());const options={url:'http://127.0.0.1:'+server.address().port,secret:'sb_secret_test',confirmBucket:BUCKET};assert.deepEqual(await applyMigration(plan,options),{uploaded:1,verified:1});assert.deepEqual(await applyMigration(plan,options),{uploaded:0,verified:1});assert.equal(uploads,1);objects.set('abc',Buffer.from('changed'));await assert.rejects(()=>applyMigration(plan,options),/CHECKSUM/);assert.equal(uploads,1);publicBucket=true;await assert.rejects(()=>applyMigration(plan,options),/PRIVATE/);await assert.rejects(()=>applyMigration(plan,{...options,confirmBucket:null}),/CONFIRMATION/);}
 finally{await new Promise(resolve=>server.close(resolve));await f.clean();}
});
