package ru.mpei.assistant.security

import org.junit.Test

class SecurityPoliciesTest {
    @Test fun urlAndAttachmentRegressionChecks() = SecurityPolicyChecks.runAll()
}
