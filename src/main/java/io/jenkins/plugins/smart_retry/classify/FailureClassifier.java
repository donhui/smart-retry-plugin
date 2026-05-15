package io.jenkins.plugins.smart_retry.classify;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.plugins.smart_retry.model.FailureClassification;

public interface FailureClassifier {

    FailureClassification classify(Throwable error, @CheckForNull String messageContext);
}
