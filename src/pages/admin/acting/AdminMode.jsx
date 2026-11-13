import EditAdminNameDialog from '../../superadmin/components/EditAdminNameDialog';
import ActiveModeRecovery from './ActiveModeRecovery';
import {useCallback,useEffect,useLayoutEffect,useMemo,useRef,useState} from 'react';
import {Navigate,Outlet,useNavigate,useParams} from 'react-router-dom';
import PageShell from '../../../components/shared/PageShell';
import Topbar from '../../../components/shared/Topbar';
import Dialog from '../../../components/shared/Dialog';
import Button from '../../../components/shared/Button';
import {SUPERADMIN_NAV_ITEMS} from '../../../data/navigationData';
import {useRemoteData} from '../../../hooks/useRemoteData';
import {AdminWorkflowContext} from '../../../context/admin/useAdminWorkflow';
import {getAdminTargets,createAdminSession,validateAdminSession,revokeAdminSession,createAdminScope} from '../../../services/admin/adminModeService';
import styles from './ProfessorMode.module.css';
const selection='/superadmin/admin-mode';
const valid=(s,id)=>s?.id===id&&s?.target?.role==='ADMIN'&&s.target.userId&&Date.parse(s.expiresAt)>Date.now();
export function AdminModeSelector(){
 const [nameTarget,setNameTarget]=useState(null);
 const navigate=useNavigate();const [page,setPage]=useState(0),[search,setSearch]=useState(''),[target,setTarget]=useState(null),[pending,setPending]=useState(false),[error,setError]=useState('');const lock=useRef(false),alive=useRef(false);
 useEffect(()=>{alive.current=true;return()=>{alive.current=false;};},[]);
 const load=useCallback(c=>getAdminTargets(page,search,c),[page,search]);const result=useRemoteData(load);
 async function enter(){if(lock.current||!target)return;lock.current=true;setPending(true);setError('');try{const s=await createAdminSession(target.userId);if(!alive.current){revokeAdminSession(s.id).catch(()=>{});return;}if(!valid(s,s.id)||s.target.userId!==target.userId)throw new Error('Invalid session');navigate(selection+'/'+encodeURIComponent(s.id));}catch(e){window.dispatchEvent(new Event('halo:mode-conflict'));if(e.response?.data?.code==='ACTING_MODE_ALREADY_ACTIVE')setTarget(null);if(alive.current)setError(e.response?.data?.code==='ACTING_MODE_ALREADY_ACTIVE'?'Exit your current mode before starting another mode.':'Could not enter Admin Mode. Check the selected account and try again.');}finally{lock.current=false;if(alive.current)setPending(false);}}
 return <PageShell responsive navItems={SUPERADMIN_NAV_ITEMS} roleBadge="Super Admin"><Topbar title="Admin Mode"/><main className={styles.selector}><p>Select an active Admin. Operations use their Admin permissions and retain your Super Admin audit identity.</p><div className={styles.searchArea}><label htmlFor="admin-target-search">Search Admins</label><div className={styles.searchField}><input id="admin-target-search" value={search} maxLength={100} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></div></div>
 {result.isLoading?<p role="status">Loading Admins...</p>:result.error?<div role="alert">Could not load Admins. <button onClick={result.reload}>Retry</button></div>:!result.data?.content?.length?<p>No active Admins found.</p>:<ul className={styles.targets}>{result.data.content.map(t=><li key={t.userId}><div><strong>{t.name}</strong><p>{t.email}</p></div><div className={styles.actions}><Button variant="secondary" onClick={event=>{event.currentTarget.focus();setNameTarget(t);}}>Edit Admin</Button><Button onClick={()=>setTarget(t)}>Select Admin</Button></div></li>)}</ul>}
 <nav className={styles.pagination} aria-label="Admin selection pages"><button disabled={page===0||result.isLoading} onClick={()=>setPage(p=>p-1)}>Previous</button><span> Page {page+1} </span><button disabled={result.isLoading||page+1>=(result.data?.totalPages??0)} onClick={()=>setPage(p=>p+1)}>Next</button></nav></main>
 {nameTarget&&<EditAdminNameDialog key={nameTarget.userId} admin={nameTarget} onClose={()=>setNameTarget(null)} onSaved={updated=>result.setData(current=>({...current,content:current.content.map(admin=>admin.userId===updated.id?{...admin,name:updated.name,email:updated.email}:admin)}))}/>}
 {target&&<Dialog className={styles.confirmDialog} labelledBy="admin-mode-title" busy={pending} onClose={()=>setTarget(null)} initialFocus="button"><h2 id="admin-mode-title">Act as Admin?</h2><p>{target.name}</p><p>Changes affect real accounts and will be audited under your Super Admin identity. Exit this mode before selecting another role.</p>{error&&<p role="alert">{error}</p>}<div className={styles.actions}><Button variant="secondary" disabled={pending} onClick={()=>setTarget(null)}>Cancel</Button><Button disabled={pending} onClick={enter}>{pending?'Starting...':'Enter Admin Mode'}</Button></div></Dialog>}</PageShell>;
}
export default function AdminMode(){const {actingSessionId}=useParams();return <Restore key={actingSessionId} id={actingSessionId}/>;}
function Restore({id}){const load=useCallback(c=>validateAdminSession(id,c),[id]);const result=useRemoteData(load);if(result.isLoading)return <p role="status">Restoring Admin Mode...</p>;if(result.error){if([400,403,404,409].includes(result.error.response?.status))return <Navigate to={selection} replace/>;return <div role="alert">Could not restore Admin Mode. <ActiveModeRecovery/><button onClick={result.reload}>Retry</button></div>;}if(!valid(result.data,id))return <Navigate to={selection} replace/>;return <Active session={result.data}/>;}
function Active({session}){
 const navigate=useNavigate();const [leaving,setLeaving]=useState(false),[exitError,setError]=useState('');const lock=useRef(false),alive=useRef(false);
 const invalid=useCallback(()=>navigate(selection,{replace:true}),[navigate]);const scope=useMemo(()=>createAdminScope(session.id,invalid),[session.id,invalid]);
 useLayoutEffect(()=>{alive.current=true;scope.resume();const timer=setTimeout(invalid,Math.max(0,Date.parse(session.expiresAt)-Date.now()));return()=>{alive.current=false;scope.dispose();clearTimeout(timer);};},[scope,session.expiresAt,invalid]);
 async function leave(){if(lock.current)return;lock.current=true;setLeaving(true);scope.dispose();try{await revokeAdminSession(session.id);if(alive.current)navigate('/superadmin',{replace:true});}catch(e){if(!alive.current)return;if([404,409].includes(e.response?.status))navigate('/superadmin',{replace:true});else{scope.resume();setError('Could not end Admin Mode. Please try again.');setLeaving(false);}}finally{lock.current=false;}}
 const value={acting:true,target:session.target,basePath:selection+'/'+session.id,api:scope.api,leave,leaving,exitError};return <AdminWorkflowContext.Provider value={value}>{leaving?<p role="status">Ending Admin Mode...</p>:<Outlet/>}</AdminWorkflowContext.Provider>;
}
