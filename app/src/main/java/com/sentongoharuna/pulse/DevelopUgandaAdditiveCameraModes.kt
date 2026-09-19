package com.sentongoharuna.pulse

/** Thin activity entries only: all camera, permission and settings work stays
 * in DevelopUgandaCameraActivity. */
class DevelopUgandaTikTokCameraActivity : DevelopUgandaCameraActivity() {
    override fun defaultCameraExperienceId(): String =
        DevelopUgandaModeProfiles.tikTok.experienceId
}

class DevelopUgandaStatusCameraActivity : DevelopUgandaCameraActivity() {
    override fun defaultCameraExperienceId(): String =
        DevelopUgandaModeProfiles.status.experienceId
}

class DevelopUgandaInterviewCameraActivity : DevelopUgandaCameraActivity() {
    override fun defaultCameraExperienceId(): String =
        DevelopUgandaModeProfiles.interview.experienceId
}
