import { useEffect, useRef, useState } from 'react';
import { Link, NavLink } from 'react-router-dom';
import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { enrollInSubject } from '../../../services/student/studentService';
import { yearLabels } from '../../../utils/registration';
import StudentPageShell from './StudentPageShell';
import Topbar from '../../../components/shared/Topbar';
import CurriculumPreview from './CurriculumPreview';
import AcademicProfileNotice from './AcademicProfileNotice';
import styles from '../styles/EnrollmentSubjects.module.css';

export default function EnrollmentSubjects({ browse = false }) {
 const workflow = useStudentWorkflow();
 // Remount local request/edit state on view or acting target changes.
 return <SubjectList key={`${workflow.identity ?? workflow.basePath}:${browse}`} workflow={workflow} browse={browse} />;
}
function SubjectList({ workflow, browse }) {
 const [rows,setRows]=useState([]),[loading,setLoading]=useState(true),[error,setError]=useState(''),[retry,setRetry]=useState(0);
 const [busy,setBusy]=useState(null),[saveError,setSaveError]=useState(''),[success,setSuccess]=useState('');
 const [curriculumSubject,setCurriculumSubject]=useState(null);
 const locked=useRef(false), mounted=useRef(true);
 const load=browse?workflow.api.getBrowseSubjects:workflow.api.getMySubjects;
 useEffect(()=>{mounted.current=true;return()=>{mounted.current=false;};},[]);
 useEffect(()=>{
  const controller=new AbortController();
  async function fetchSubjects(){setLoading(true);setError('');
  await load({signal:controller.signal}).then(data=>{if(!controller.signal.aborted)setRows(data);}).catch(failure=>{
   if(!controller.signal.aborted)setError(message(failure));
  }).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  }
  fetchSubjects();
  return()=>controller.abort();
 },[load,retry]);
 async function enroll(subject){
  if(locked.current || workflow.acting)return;
  locked.current=true;setBusy(subject.subjectId);setSaveError('');setSuccess('');
  try{
   await enrollInSubject(subject.subjectId);
   if(!mounted.current)return;
   setSuccess(`You are enrolled in ${subject.subjectName}.`);
   // Always refresh authoritative eligibility/enrollment state after success.
   setRetry(value=>value+1);
  }catch(failure){if(mounted.current)setSaveError(message(failure));}
  finally{locked.current=false;if(mounted.current)setBusy(null);}
 }
 const base=workflow.basePath;
 return <StudentPageShell><Topbar title={browse?'Browse Subjects':'My Subjects'} />
  <main className={styles.page}>
   <nav aria-label="Subject views" className={styles.links}><NavLink end to={`${base}/subjects/my`}>My Subjects</NavLink><NavLink end to={`${base}/subjects/browse`}>Browse Subjects</NavLink></nav>
   {workflow.acting && <p className={styles.muted}>Enrollment is read-only in {workflow.preview?'Preview':'Support'} Mode.</p>}
   {success && <p role="status" className={styles.notice}>{success} <Link to={`${base}/subjects/my`}>View My Subjects</Link></p>}
   {saveError && <div role="alert"><p>{saveError === 'PROFILE_INCOMPLETE' ? 'Your academic profile is incomplete. Contact an administrator before enrolling.' : saveError}</p><button type="button" disabled={loading} onClick={()=>setRetry(n=>n+1)}>Refresh subjects</button></div>}
   {loading?<p role="status">Loading subjects...</p>:error==='PROFILE_INCOMPLETE'?<AcademicProfileNotice onRefresh={()=>setRetry(n=>n+1)} />:error?<div role="alert"><p>{error}</p><button type="button" onClick={()=>setRetry(n=>n+1)}>Retry</button></div>:rows.length===0?<div className={styles.empty}><p>{browse?'No subjects are currently available for enrollment.':"You haven't enrolled in any subjects yet."}</p>{!browse && <Link to={`${base}/subjects/browse`}>Browse Subjects</Link>}</div>:<div className={styles.grid}>
    {rows.map(subject=><article key={subject.subjectId} className={styles.card}>
     <div className={styles.cardHeader}><h2>{subject.subjectName}</h2><span className={styles.badge}>{browse?'Available':'Enrolled'}</span></div>
     <ul className={styles.metadata} aria-label="Subject details">{[
       subject.subjectCode,
       yearLabels[subject.yearLevel] ?? subject.yearLevel,
       subject.totalWeeks != null ? `${subject.totalWeeks} ${subject.totalWeeks === 1 ? 'week' : 'weeks'}` : null
     ].filter(value => value != null && String(value).trim() !== '').map((value,index) => <li key={index}>{value}</li>)}</ul>
     {!browse && <div className={styles.progress}><label htmlFor={`subject-progress-${subject.subjectId}`}>Learning progress <strong>{subject.progressPercentage}%</strong></label><progress id={`subject-progress-${subject.subjectId}`} max="100" value={subject.progressPercentage} /></div>}
     {browse?<div className={styles.cardActions}>{!workflow.acting && <button className={styles.primary} type="button" disabled={busy!==null} onClick={()=>enroll(subject)}>{busy===subject.subjectId?'Enrolling...':'Enroll in Subject'}</button>}<button type="button" className={styles.secondary} onClick={()=>setCurriculumSubject(subject.subjectId)}>View Curriculum</button></div>:<Link className={styles.primary} to={`${base}/subjects?subject=${subject.subjectId}`}>{isCompletedSubject(subject) ? 'Review Subject' : 'Continue Learning'}</Link>}
    </article>)}
   </div>}
   {curriculumSubject !== null && <CurriculumPreview key={curriculumSubject} subjectId={curriculumSubject} load={workflow.api.getSubjectCurriculum} onClose={()=>setCurriculumSubject(null)} />}
  </main>
 </StudentPageShell>;
}
function message(error){
 const status=error?.response?.status,code=error?.response?.data?.code;
 if(code==='PROFILE_INCOMPLETE')return code;
 if(status===401)return 'Your session has expired. Please sign in again.';
 if(status===403)return 'You are not authorized to access these subjects.';
 if(status===404)return 'This subject is no longer available. Refresh the list and try again.';
 if(status===409)return 'Enrollment could not be completed. Refresh the list and try again.';
 return error?.response?'HALO could not complete this request. Please try again.':'Could not reach HALO. Check your connection and try again.';
}

// Exact counts follow the existing all-weeks completion presentation. Never infer from a rounded percentage.
function isCompletedSubject(subject) {
 return Number.isInteger(subject.totalWeeks) && subject.totalWeeks > 0
  && subject.completedWeeks === subject.totalWeeks
  && subject.eligibleModuleCount === subject.totalWeeks;
}
