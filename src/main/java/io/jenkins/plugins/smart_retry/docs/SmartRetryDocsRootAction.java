package io.jenkins.plugins.smart_retry.docs;

import hudson.Extension;
import hudson.model.RootAction;
import io.jenkins.plugins.smart_retry.model.SmartRetryReferenceCatalog;
import java.util.List;
import java.util.stream.Collectors;

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

    public List<SmartRetryReferenceCatalog.MatchedRuleGroup> getMatchedRuleGroups() {
        return SmartRetryReferenceCatalog.matchedRuleGroups();
    }

    public List<SmartRetryReferenceCatalog.CustomRuleDoc> getCustomRules() {
        return SmartRetryReferenceCatalog.customRules();
    }

    public List<String> getFailureTypeNames() {
        return SmartRetryReferenceCatalog.failureTypes().stream()
                .map(SmartRetryReferenceCatalog.FailureTypeDoc::getName)
                .collect(Collectors.toList());
    }
}
