import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import api from '../../../services/apiClient';
import { useRemoteData } from '../../../hooks/useRemoteData';
import styles from './ProfessorMode.module.css';
export default function ActiveModeRecovery() {
 const load=useCallback(c=>api.get('/admin/acting/active',c).then(r=>r.data),[]);
 const result=useRemoteData(load),navigate=useNavigate();
 const {reload}=result;
 const [pending,setPending]=useState(false),[error,setError]=useState('');const lock=useRef(false);
 useEffect(()=>{const refresh=()=>reload();window.addEventListener('halo:mode-conflict',refresh);return()=>window.removeEventListener('halo:mode-conflict',refresh);},[reload]);
 async function exit(id){if(lock.current)return;lock.current=true;setPending(true);setError('');try{await api.delete('/admin/acting/active/'+encodeURIComponent(id));await result.reload();navigate('/superadmin',{replace:true});}catch{setError('Could not end the active mode. Retry Return to Super Admin.');}finally{lock.current=false;setPending(false);}}
 if(result.error)return <section role="alert">Could not check active modes. <button onClick={result.reload}>Retry mode check</button></section>;
 return (Array.isArray(result.data)?result.data:[]).map(({session,preview})=>{const role=session.target.role.toLowerCase();return <section key={session.id} className={styles.banner} aria-label="Active mode recovery"><strong>{role==='admin'?'Admin':role==='professor'?'Professor':'Student'} {preview?'Preview':'Mode'} is active: {session.target.name}</strong><p>Resume this mode or end it before starting another.</p><div className={styles.actions}><Link to={'/superadmin/'+role+(preview?'-preview/':'-mode/')+encodeURIComponent(session.id)}>Resume active mode</Link><button disabled={pending} onClick={()=>exit(session.id)}>{pending?'Ending mode...':'Return to Super Admin'}</button></div>{error&&<p role="alert">{error}</p>}</section>;});
}
