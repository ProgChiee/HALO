import { useAuth } from '../../../context/login/useAuth';
import { ADMIN_NAV_ITEMS, SUPERADMIN_NAV_ITEMS } from '../../../data/navigationData';
export function useModeOwner(){const {role}=useAuth();const superAdmin=role==='superadmin';return {root:superAdmin?'/superadmin':'/admin',nav:superAdmin?SUPERADMIN_NAV_ITEMS:ADMIN_NAV_ITEMS,label:superAdmin?'Super Admin':'Admin'};}
