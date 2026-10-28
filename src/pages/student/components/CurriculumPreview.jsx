import { useEffect, useState, useId } from 'react';
import Dialog from '../../../components/shared/Dialog';
import { yearLabels } from '../../../utils/registration';
import styles from '../styles/CurriculumPreview.module.css';

export default function CurriculumPreview({ subjectId, load, onClose }) {
 const titleId=useId();
 const [data,setData]=useState(null),[loading,setLoading]=useState(true),[error,setError]=useState(null),[retry,setRetry]=useState(0);
 useEffect(()=>{
  const controller=new AbortController();
  async function fetchCurriculum(){
   setLoading(true);setError(null);setData(null);
   try{const response=await load(subjectId,{signal:controller.signal});if(!controller.signal.aborted)setData(response);}
   catch(failure){if(!controller.signal.aborted)setError(curriculumError(failure));}
   finally{if(!controller.signal.aborted)setLoading(false);}
  }
  fetchCurriculum();return()=>controller.abort();
 },[subjectId,load,retry]);
 return <Dialog labelledBy={titleId} onClose={onClose} closeOnEscape className={styles.dialog} initialFocus="button">
  <header className={styles.header}><h2 id={titleId}>Curriculum Preview</h2><button type="button" onClick={onClose}>Close</button></header>
  {loading?<p role="status">Loading curriculum...</p>:error?<div role="alert"><p>{error.message}</p>{error.retry && <button type="button" onClick={()=>{setLoading(true);setError(null);setRetry(n=>n+1);}}>Retry</button>}</div>:data && <>
   <h3>{data.subjectName}</h3>
   <p aria-label="Subject metadata" className={`${styles.meta} ${styles.metadata}`}>{[
    data.subjectCode,
    yearLabels[data.yearLevel],
    data.publishedWeekCount != null ? `${data.publishedWeekCount} Published ${data.publishedWeekCount===1?'Week':'Weeks'}` : null
   ].filter(Boolean).map((value,index)=><span key={index}>{value}</span>)}</p>
   <p className={styles.meta}>Preview of published lesson titles.</p>
   {data.weeks?.length?<ol className={styles.weeks}>{data.weeks.map((week,index)=><li key={`${week.weekNumber}-${index}`}><span className={styles.meta}>Week {week.weekNumber}</span><p>{week.title || 'Lesson title not set'}</p></li>)}</ol>:<p>No published weeks or lessons are available yet.</p>}
   <p className={`${styles.meta} ${styles.accessNote}`}>Enroll in this subject to access its learning materials and assessments, subject to availability and access requirements.</p>
  </>}
 </Dialog>;
}
function curriculumError(error){
 const status=error?.response?.status;
 if(status===401)return {message:'Your session has expired. Please sign in again.'};
 if(status===403)return {message:'You are not authorized to preview this curriculum.'};
 if(status===404)return {message:'This subject is no longer available for curriculum preview.'};
 if(error?.response?.data?.code==='PROFILE_INCOMPLETE')return {message:'Your academic profile is incomplete. Contact an administrator.'};
 return {message:error?.response?'Curriculum could not be loaded. Please try again.':'Could not reach HALO. Check your connection and try again.',retry:true};
}
