import { createContext, useContext } from 'react';
import { createStudentService } from '../../services/student/studentService';
import { createQuizService } from '../../services/student/quizService';
import { createMentorService } from '../../services/student/aiMentorService';
const normal = { api: { ...createStudentService(), ...createQuizService(), ...createMentorService() }, basePath: '/student', acting: false, identity: 'student' };
export const StudentWorkflowContext = createContext(normal);
export function useStudentWorkflow() { return useContext(StudentWorkflowContext); }
