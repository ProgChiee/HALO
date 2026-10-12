import ProfilePhoto from '../../../components/shared/ProfilePhoto';
import { useStudentWorkflow } from '../../../context/student/useStudentWorkflow';
export default function StudentProfilePhoto(props) {
  const workflow = useStudentWorkflow();
  return <ProfilePhoto {...props} identity={workflow.identity} getProfilePhoto={workflow.api.getProfilePhoto} />;
}
