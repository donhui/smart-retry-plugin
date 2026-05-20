package io.jenkins.plugins.smart_retry.docs;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.smart_retry.model.SmartRetryReferenceCatalog;
import java.util.List;

@Extension
public final class SmartRetryDocsRootAction implements RootAction {

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Smart Retry Docs";
    }

    @Override
    public String getUrlName() {
        return "smartRetryDocs";
    }

    public List<SmartRetryReferenceCatalog.FailureTypeDoc> getFailureTypes() {
        return SmartRetryReferenceCatalog.failureTypes();
    }

    public List<SmartRetryReferenceCatalog.MatchedRuleDoc> getMatchedRules() {
        return SmartRetryReferenceCatalog.matchedRules();
    }
}
