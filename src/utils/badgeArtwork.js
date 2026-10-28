import artwork0 from '../assets/badge/first-step.png';
import artwork1 from '../assets/badge/first-try.png';
import artwork2 from '../assets/badge/knowledge-seeker.png';
import artwork3 from '../assets/badge/perfect-score.png';
import artwork4 from '../assets/badge/never-give-up.png';
import artwork5 from '../assets/badge/comeback-stronger.png';
import artwork6 from '../assets/badge/module-finisher.png';
import artwork7 from '../assets/badge/subject-master.png';

import gettingStarted from '../assets/badge/getting-started.png';
import dedicatedLearner from '../assets/badge/dedicated-learner.png';
import assessmentAce from '../assets/badge/assessment-ace.png';
import haloAchiever from '../assets/badge/halo-achiever.png';

// Match stable BadgeType codes only; unmatched definitions retain the medal fallback.
const artwork = Object.freeze({
  FIRST_STEP: artwork0,
  FIRST_TRY: artwork1,
  KNOWLEDGE_SEEKER: artwork2,
  PERFECT_SCORE: artwork3,
  NEVER_GIVE_UP: artwork4,
  COMEBACK_STRONGER: artwork5,
  MODULE_FINISHER: artwork6,
  SUBJECT_MASTER: artwork7,
  GETTING_STARTED: gettingStarted,
  DEDICATED_LEARNER: dedicatedLearner,
  ASSESSMENT_ACE: assessmentAce,
  HALO_ACHIEVER: haloAchiever,
});

export function getBadgeArtwork(badgeType) {
  return Object.hasOwn(artwork, badgeType) ? artwork[badgeType] : null;
}
