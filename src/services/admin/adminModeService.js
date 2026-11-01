import axios from 'axios';
import client from '../apiClient';
import { createAdminService } from './adminService';
export const getAdminTargets=(page,search,config={})=>client.get('/admin/acting/targets',{...config,params:{role:'ADMIN',page,size:20,search}}).then(r=>r.data);
export const createAdminSession=targetUserId=>client.post('/admin/acting/sessions',{targetUserId,targetRole:'ADMIN'}).then(r=>r.data);
export const validateAdminSession=(id,config={})=>client.get('/admin/acting/sessions/'+encodeURIComponent(id),config).then(r=>r.data);
export const revokeAdminSession=id=>client.delete('/admin/acting/sessions/'+encodeURIComponent(id));
export function createAdminScope(id,onInvalid){
 let active=true;const pending=new Set();
 async function request(method,path,data,config={}){
  if(!active)throw new axios.CanceledError();
  if(!path.startsWith('/admin/') || path.startsWith('/admin/acting/') || path.startsWith('/admin/preview/'))throw new Error('Unsupported Admin operation');
  const controller=new AbortController();pending.add(controller);const abort=()=>controller.abort();config.signal?.addEventListener('abort',abort,{once:true});if(config.signal?.aborted)abort();
  try{const result=await client.request({...config,method,data,url:'/super-admin/acting/admin/'+path.slice(7),headers:{...config.headers,'X-Acting-Session':id},signal:controller.signal});if(!active||controller.signal.aborted)throw new axios.CanceledError();return result;}
  catch(error){if(!active||controller.signal.aborted)throw new axios.CanceledError();if(error.response?.data?.code==='ACTING_SESSION_INVALID')onInvalid();else if([403,404].includes(error.response?.status)){try{await validateAdminSession(id,{signal:controller.signal});}catch(validation){if(active&&!controller.signal.aborted&&[400,403,404,409].includes(validation.response?.status))onInvalid();}}throw error;}
  finally{pending.delete(controller);config.signal?.removeEventListener('abort',abort);}
 }
 const api=createAdminService({get:(p,c)=>request('get',p,undefined,c),post:(p,d,c)=>request('post',p,d,c),put:(p,d,c)=>request('put',p,d,c),patch:(p,d,c)=>request('patch',p,d,c)});
 return {api,resume(){active=true;},dispose(){active=false;pending.forEach(c=>c.abort());pending.clear();}};
}
