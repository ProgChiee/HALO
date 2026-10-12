import { useCallback, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
import { useRemoteData } from '../../../hooks/useRemoteData';
import Button from '../../../components/shared/Button';
import styles from '../styles/StudyCalendar.module.css';

export default function StudyCalendar() {
 const { identity }=useStudentWorkflow();
 return <Calendar key={identity} />;
}
function Calendar(){
 const [open,setOpen]=useState(false),[month,setMonth]=useState(undefined);
 return <div className={styles.container}>
  <div className={styles.header}><h3>Study Calendar</h3><button className={styles.toggle} aria-expanded={open} aria-controls="study-calendar" onClick={()=>setOpen(!open)}>{open ? <>Hide<span className={styles.srOnly}> Study Calendar</span></> : 'View Study Calendar'}</button></div>
  {open && <section id="study-calendar" aria-label="Study Calendar"><Month key={month ?? 'current'} month={month} changeMonth={setMonth} /></section>}
 </div>;
}
function Month({month,changeMonth}){
 const {api}=useStudentWorkflow();
 const load=useCallback(config=>api.getStudyCalendar(month,config),[api,month]);
 const {data,isLoading,error,reload}=useRemoteData(load);
 if(isLoading)return <p role="status">Loading study calendar...</p>;
 if(error)return <div role="alert"><p>Could not load your study calendar.</p><Button onClick={reload}>Retry calendar</Button></div>;
 if(!data)return null;
 const [year,number]=data.month.split('-').map(Number);
 const start=new Date(Date.UTC(year,number-1,1));
 const count=new Date(Date.UTC(year,number,0)).getUTCDate(),offset=(start.getUTCDay()+6)%7;
 const active=new Set(data.activeDates);
 const label=new Intl.DateTimeFormat('en',{month:'long',year:'numeric',timeZone:'UTC'}).format(start);
 const move=delta=>{const date=new Date(Date.UTC(year,number-1+delta,1));changeMonth(date.toISOString().slice(0,7));};
 const cells=Array.from({length:Math.ceil((offset+count)/7)*7},(_,i)=>i>=offset&&i<offset+count?i-offset+1:null);
 return <>
  <div className={styles.navigation}>
   <button className={styles.monthButton} disabled={year===1000&&number===1} onClick={()=>move(-1)}><ChevronLeft size={18} aria-hidden="true" /><span className={styles.srOnly}>Previous month</span></button>
   <span aria-hidden="true">{label}</span>
   <button className={styles.monthButton} disabled={year===9998&&number===12} onClick={()=>move(1)}><ChevronRight size={18} aria-hidden="true" /><span className={styles.srOnly}>Next month</span></button>
  </div>
  <table className={styles.calendar}><caption className={styles.srOnly}>{label}</caption><thead><tr>{['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map(day=><th scope="col" key={day}>{day}</th>)}</tr></thead>
   <tbody>{Array.from({length:cells.length/7},(_,row)=><tr key={row}>{cells.slice(row*7,row*7+7).map((day,index)=>{
    if(day===null)return <td key={index} />;
    const date=data.month+'-'+String(day).padStart(2,'0'),studied=date<=data.today&&active.has(date),today=date===data.today;
    return <td key={index} className={studied?styles.active:undefined} aria-current={today?'date':undefined} aria-label={date+(today?', Today':'')+(studied?', Active study day':', No study activity')}><span>{day}</span>{studied&&<span aria-hidden="true"> &#10003;</span>}</td>;
   })}</tr>)}</tbody>
  </table>
  <p className={styles.help}>&#10003; Active study day &bull; Outline = Today. Dates use {data.timezone}.</p>
 </>;
}
