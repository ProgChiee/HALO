import { useEffect, useRef, useState } from 'react';
import PageShell from '../../../components/shared/PageShell';
import Button from '../../../components/shared/Button';
import Dialog from '../../../components/shared/Dialog';
import { ADMIN_NAV_ITEMS } from '../../../data/navigationData';
import api from '../../../services/apiClient';
import styles from '../styles/StudentRegistry.module.css';

export default function StudentRegistry() {
  const [search,setSearch]=useState(''),[eligibility,setEligibility]=useState('ALL'),[registration,setRegistration]=useState('ALL'),[page,setPage]=useState(0);
  const [data,setData]=useState(null),[history,setHistory]=useState([]),[file,setFile]=useState(null),[preview,setPreview]=useState(null),[confirm,setConfirm]=useState(false);
  const [busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState(''),[revision,setRevision]=useState(0);
  const lock=useRef(false);
  useEffect(()=>{let alive=true;const timer=setTimeout(()=>{setLoading(true);api.get('/admin/student-registry',{params:{search,eligibility,registration,page,size:20}}).then(r=>{if(alive){setData(r.data);const last=Math.max(0,Math.ceil(r.data.totalElements/20)-1);if(page>last)setPage(last);}}).catch(()=>{if(alive)setError('Could not load the registry. Please retry.');}).finally(()=>{if(alive)setLoading(false);});},200);return()=>{alive=false;clearTimeout(timer);};},[search,eligibility,registration,page,revision]);
  useEffect(()=>{let alive=true;api.get('/admin/student-registry/history').then(r=>{if(alive)setHistory(r.data);}).catch(()=>{if(alive)setError('Import history could not be loaded. Please retry.');});return()=>{alive=false;};},[revision]);
  async function run(action){if(lock.current)return;lock.current=true;setBusy(true);setError('');setNotice('');try{await action();}catch(e){setError(e.response?.status===401?'Your session could not be authenticated. Sign in again, then retry.':e.response?.status===403?'Only an authorized Admin outside acting mode can use the Student Registry.':e.response?.status===503?'Student Registry is unavailable. Check that the local registry migration has been applied, or contact your administrator.':e.response?.status===409?'The registry changed. Preview the file again; nothing was saved.':'Could not process the request. Use a plain .xlsx workbook with one sheet, required headers, no formulas, at most 1,000 rows and 2 MB.');}finally{lock.current=false;setBusy(false);}}
  function upload(){const form=new FormData();form.append('file',file);return form;}
  async function template(){await run(async()=>{const r=await api.get('/admin/student-registry/template',{responseType:'blob'});const url=URL.createObjectURL(r.data);const a=document.createElement('a');a.href=url;a.download='HALO-student-registry.xlsx';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});}
  function change(setter,value){setter(value);setPage(0);}
  return <PageShell navItems={ADMIN_NAV_ITEMS} roleBadge="Admin" responsive><main className={styles.main}>
    <h1>Student Registry</h1><p>Import the official AHRT eligibility list. Importing does not create accounts or change existing Students.</p>
    {error&&<p role="alert">{error} <button onClick={()=>{setError('');setRevision(v=>v+1);}}>Retry lists</button></p>}{notice&&<p role="status">{notice}</p>}
    <section className={styles.card} aria-label="Import students"><h2>Excel import</h2>
      <p>Download the template, replace its guidance row, and keep the four headers. Only valid eligible rows are saved; all other rows are skipped. Existing records are never overwritten.</p>
      <div className={styles.controls}><Button onClick={template} disabled={busy}>Download template</Button><label>Excel file (.xlsx, maximum 2 MB)<input type="file" accept=".xlsx" disabled={busy} onChange={e=>{const next=e.target.files?.[0];setPreview(null);setFile(null);setError('');if(next){if(!next.name.toLowerCase().endsWith('.xlsx')||next.size>2097152||!next.size)setError('Choose a nonempty .xlsx file up to 2 MB.');else setFile(next);}}}/></label>
      <Button disabled={!file||busy} onClick={()=>run(async()=>setPreview((await api.post('/admin/student-registry/preview',upload())).data))}>Preview import</Button></div>
      {busy&&<p role="status">Processing...</p>}
      {preview&&<><p role="status">{preview.total} rows: {preview.eligible} eligible, {preview.ineligible} ineligible, {preview.invalid} invalid, {preview.duplicates} duplicates.</p>
        <div className={styles.scroll}><table><thead><tr>{['Row','Student ID','Full name','Email','Course','Result'].map(x=><th key={x}>{x}</th>)}</tr></thead><tbody>{preview.rows.map(r=><tr key={r.row}><td>{r.row}</td><td>{r.studentId}</td><td>{r.fullName}</td><td>{r.email}</td><td>{r.course}</td><td>{r.status}{r.errors.map(e=><p key={e}>{e}</p>)}</td></tr>)}</tbody></table></div>
        <div className={styles.controls}><Button disabled={busy||!preview.eligible} onClick={()=>setConfirm(true)}>Import {preview.eligible} eligible rows</Button><Button disabled={busy} variant="secondary" onClick={()=>{setPreview(null);setFile(null);}}>Cancel import</Button></div></>}
    </section>
    <section className={styles.card}><h2>Official records</h2><div className={styles.controls}>
      <label>Search<input value={search} maxLength={100} placeholder="Student ID, name or email" onChange={e=>change(setSearch,e.target.value)}/></label>
      <label>Eligibility<select value={eligibility} onChange={e=>change(setEligibility,e.target.value)}><option value="ALL">All eligibility statuses</option><option value="ELIGIBLE">Eligible</option><option value="INELIGIBLE">Ineligible</option></select></label>
      <label>Registration<select value={registration} onChange={e=>change(setRegistration,e.target.value)}><option value="ALL">All registration statuses</option><option value="UNREGISTERED">Unregistered</option><option value="REGISTERED">Registered</option></select></label></div>
      {loading?<p role="status">Loading registry...</p>:!data?.content.length?<p>No registry records found.</p>:<div className={styles.scroll}><table><thead><tr><th>Student ID</th><th>Full name</th><th>Email</th><th>Course</th><th>Eligibility</th><th>Registration</th></tr></thead><tbody>{data.content.map(r=><tr key={r.id}><td>{r.studentId}</td><td>{r.fullName}</td><td>{r.email}</td><td>{r.course}</td><td>{r.eligibilityStatus}</td><td>{r.registrationStatus}</td></tr>)}</tbody></table></div>}
      <div className={styles.controls}><Button disabled={loading||page===0} onClick={()=>setPage(p=>p-1)}>Previous</Button><span>Page {page+1}  /  {data?.totalElements??0} records</span><Button disabled={loading||!data||(page+1)*20>=data.totalElements} onClick={()=>setPage(p=>p+1)}>Next</Button></div>
    </section>
    <section className={styles.card}><h2>Recent import history</h2><p>Latest 50 confirmed imports. Rejected files are not saved.</p>{!history.length?<p>No imports yet.</p>:<ul>{history.map(h=><li key={h.id}>Import #{h.id}  /  {String(h.imported_at).replace('T',' ')}  /  {h.imported_rows} imported, {h.skipped_rows} skipped of {h.total_rows}</li>)}</ul>}</section>
    {confirm&&<Dialog className={styles.dialog} labelledBy="registry-confirm-title" busy={busy} closeOnEscape onClose={()=>{if(!busy)setConfirm(false);}}><h2 id="registry-confirm-title">Confirm Student Registry import</h2><p>Import eligible rows only? The backend will revalidate the file. Invalid, duplicate and other-course rows will be skipped.</p><div className={styles.controls}><Button disabled={busy} onClick={()=>run(async()=>{const body=upload();body.append('confirmed','true');const r=(await api.post('/admin/student-registry/confirm',body)).data;setConfirm(false);setPreview(null);setFile(null);setNotice(`${r.eligible} records imported; ${r.total-r.eligible} skipped.`);setRevision(v=>v+1);})}>Confirm import</Button><Button disabled={busy} variant="secondary" onClick={()=>setConfirm(false)}>Cancel</Button></div></Dialog>}
  </main></PageShell>;
}
