package com.ptc.halo.enums;

public enum BadgeType {

    // Progress
    FIRST_STEP("First Step", "Completed your first learning module."),
    GETTING_STARTED("Getting Started", "Completed 3 learning modules."),
    KNOWLEDGE_SEEKER("Knowledge Seeker", "Completed 5 learning modules."),
    DEDICATED_LEARNER("Dedicated Learner", "Completed 10 learning modules."),

    // Assessment
    FIRST_TRY("First Try", "Passed an assessment on the first attempt."),
    PERFECT_SCORE("Perfect Score", "Earned a perfect score on an assessment."),
    NEVER_GIVE_UP("Never Give Up", "Passed an assessment after a previous failed attempt."),
    COMEBACK_STRONGER("Comeback Stronger", "Improved your assessment score by at least 20 points."),
    ASSESSMENT_ACE("Assessment Ace", "Passed 5 assessments."),

    // Subject Progress
    MODULE_FINISHER("Module Finisher", "Fully completed an available learning module."),
    SUBJECT_MASTER("Subject Master", "Completed every published, available module in one subject."),

    // Special
    HALO_ACHIEVER("HALO Achiever", "Earned 10 distinct ordinary HALO badges.");

    private final String badgeName;
    private final String description;

    BadgeType(String badgeName, String description) {
        this.badgeName = badgeName;
        this.description = description;
    }
    public String getBadgeName() { return badgeName; }
    public String getDescription() { return description; }
}
