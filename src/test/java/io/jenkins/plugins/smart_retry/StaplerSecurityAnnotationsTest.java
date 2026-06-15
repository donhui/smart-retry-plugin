package io.jenkins.plugins.smart_retry;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.jenkins.plugins.smart_retry.config.CustomClassificationRule;
import io.jenkins.plugins.smart_retry.config.CustomProfileSettings;
import io.jenkins.plugins.smart_retry.config.SmartRetryGlobalConfiguration;
import io.jenkins.plugins.smart_retry.step.SmartRetryStep;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.verb.POST;

class StaplerSecurityAnnotationsTest {

    @Test
    void marksSecuritySensitiveStaplerEndpointsWithPost() throws Exception {
        assertHasPost(CustomClassificationRule.DescriptorImpl.class, "doFillFailureTypeItems");
        assertHasPost(CustomClassificationRule.DescriptorImpl.class, "doCheckNameSuffix", String.class);
        assertHasPost(CustomClassificationRule.DescriptorImpl.class, "doCheckName", String.class);
        assertHasPost(CustomClassificationRule.DescriptorImpl.class, "doCheckPattern", String.class);
        assertHasPost(CustomClassificationRule.DescriptorImpl.class, "doCheckFailureType", String.class);

        assertHasPost(CustomProfileSettings.DescriptorImpl.class, "doCheckName", String.class);

        assertHasPost(
                SmartRetryGlobalConfiguration.class,
                "configure",
                org.kohsuke.stapler.StaplerRequest2.class,
                net.sf.json.JSONObject.class);
        assertHasPost(SmartRetryGlobalConfiguration.class, "doFillDefaultProfileItems");
        assertHasPost(SmartRetryGlobalConfiguration.class, "doFillBackoffItems");
        assertHasPost(SmartRetryGlobalConfiguration.class, "doCheckDefaultProfile", String.class);
        assertHasPost(SmartRetryGlobalConfiguration.class, "doCheckDisabledBuiltInRules", String.class);
        assertHasPost(SmartRetryGlobalConfiguration.class, "doCheckCustomClassificationRules", String.class);
        assertHasPost(SmartRetryGlobalConfiguration.class, "doCheckConsoleContextLines", String.class);
        assertHasPost(SmartRetryGlobalConfiguration.class, "doCheckMaxRetries", String.class);
        assertHasPost(SmartRetryGlobalConfiguration.class, "doCheckInitialDelaySeconds", String.class);

        assertHasPost(SmartRetryStep.DescriptorImpl.class, "doFillProfileItems");
        assertHasPost(SmartRetryStep.DescriptorImpl.class, "doFillBackoffItems");
        assertHasPost(SmartRetryStep.DescriptorImpl.class, "doCheckProfile", String.class);
        assertHasPost(SmartRetryStep.DescriptorImpl.class, "doCheckMaxRetries", String.class);
        assertHasPost(SmartRetryStep.DescriptorImpl.class, "doCheckInitialDelaySeconds", String.class);
    }

    private static void assertHasPost(Class<?> type, String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = type.getMethod(methodName, parameterTypes);
        assertNotNull(
                method.getAnnotation(POST.class), () -> type.getSimpleName() + "#" + methodName + " must use @POST");
    }
}
