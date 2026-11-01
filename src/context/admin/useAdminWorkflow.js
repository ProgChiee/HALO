import { createContext, useContext } from 'react';
import { createAdminService } from '../../services/admin/adminService';
const normal={acting:false,api:createAdminService()};
export const AdminWorkflowContext=createContext(normal);
export const useAdminWorkflow=()=>useContext(AdminWorkflowContext);
