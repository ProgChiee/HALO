import { Download } from 'lucide-react';
import buttons from '../../../components/shared/Button.module.css';
import styles from './ObtlTemplateDownload.module.css';

const filename = 'HALO_Official_OBTL_Excel_Template_v1.xlsx';

export default function ObtlTemplateDownload() {
  return (
    <div className={styles.download}>
      <a className={`${buttons.btn} ${buttons.secondary}`}
        href={`${import.meta.env.BASE_URL}templates/${filename}`} download={filename}>
        <Download size={16} aria-hidden="true" />
        Download OBTL Template
      </a>
      <p>Download and complete the official OBTL template before uploading your file.</p>
    </div>
  );
}
