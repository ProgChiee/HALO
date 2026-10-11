import { createContext, useContext } from 'react';
import { createProfessorService } from '../../services/professor/professorService';
const normal = { api: createProfessorService(), basePath: '/professor', acting: false };
export const ProfessorWorkflowContext = createContext(normal);
export function useProfessorWorkflow() { return useContext(ProfessorWorkflowContext); }
