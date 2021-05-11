/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package jenkins.branch.buildstrategies.basic;
import java.util.Collections;
import java.util.List;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.ACL;
import hudson.model.Run;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import jenkins.branch.BranchBuildStrategy;
import jenkins.branch.BranchBuildStrategyDescriptor;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.plugins.git.GitBranchSCMHead;
import jenkins.plugins.git.GitSCMTelescope;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.model.Jenkins;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;


/**
 * A {@link BranchBuildStrategy} that builds tags.
 *
 * @since 1.0.0
 */
public class SkipOnLastCommitAgeBuildStrategyImpl extends BranchBuildStrategy {

    private static final Logger LOGGER = Logger.getLogger(SkipOnLastCommitAgeBuildStrategyImpl.class.getName());

    private final long atMostMillis;

    /**
     * Our constructor.
     *
     * @param atMostDays the number of days old that the tag must be after which it is no longer considered for automatic build.
     */
    @DataBoundConstructor
    public SkipOnLastCommitAgeBuildStrategyImpl(@CheckForNull String atMostDays) {
        this(
                TimeUnit.DAYS,
                Long.parseLong(StringUtils.defaultIfBlank(atMostDays, "-1"))
        );
    }

    /**
     * Constructor for testing.
     *
     * @param unit    the time units.

     * @param atMost  {@code null} or {@code -1L} to disable filtering by maximum age, otherwise the maximum age
     *                expressed in the supplied time units.
     */
    public SkipOnLastCommitAgeBuildStrategyImpl(@NonNull TimeUnit unit, @CheckForNull Number atMost) {
        this.atMostMillis = atMost == null || atMost.longValue() < 0L ? -1L : unit.toMillis(atMost.longValue());
    }

    @Restricted(DoNotUse.class) // stapler form binding only
    @NonNull
    public String getAtMostDays() {
        return atMostMillis >= 0L ? Long.toString(TimeUnit.MILLISECONDS.toDays(atMostMillis)) : "";
    }

    public long getAtMostMillis() {
        return atMostMillis;
    }

    @CheckForNull
    public Long getAtMost(@NonNull TimeUnit unit) {
        return atMostMillis >= 0L ? unit.convert(atMostMillis, TimeUnit.MILLISECONDS) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head, @NonNull SCMRevision currRevision,
                                    @CheckForNull SCMRevision prevRevision) {
        return isAutomaticBuild(source, head, currRevision, prevRevision, new LogTaskListener(Logger.getLogger(getClass().getName()), Level.INFO));
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head, @NonNull SCMRevision currRevision,
                                    @CheckForNull SCMRevision prevRevision, @NonNull  TaskListener taskListener) {
        return isAutomaticBuild(source,head, currRevision, prevRevision, prevRevision, taskListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAutomaticBuild(@NonNull SCMSource source, @NonNull SCMHead head, @NonNull SCMRevision currRevision,
                                    @CheckForNull SCMRevision lastBuiltRevision, @CheckForNull SCMRevision lastSeenRevision, @NonNull TaskListener taskListener) {
            if (!(head instanceof GitBranchSCMHead)) {
                return false;
            }

            if (atMostMillis >= 0L) {
                AbstractGitSCMSource abstrct = (AbstractGitSCMSource)source;
                GitSCMTelescope telescope = GitSCMTelescope.of((GitSCMSource)source);

                final Jenkins jenkins = Jenkins.getInstance();

                final List<StandardCredentials> creds =
                    CredentialsProvider.lookupCredentials(
                        StandardCredentials.class,
                        jenkins,
                        ACL.SYSTEM,
                        Collections.<DomainRequirement>emptyList()
                    );

                String credentialsId = abstrct.getCredentialsId();

                for(StandardCredentials credentials : creds){
                    if(credentials.getId().equals(credentialsId)){
                        String remote = abstrct.getRemote();
                        long timestamp = telescope.getTimestamp(remote, credentials, head);
                        long commitAge = System.currentTimeMillis() - timestamp;

                        if (commitAge > atMostMillis) {
                            return false;
                        }
                    }
                }
                return false;
            }
            return true;
    }

    /**
     * Our descriptor.
     */
    @Extension
    public static class DescriptorImpl extends BranchBuildStrategyDescriptor {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SkipOnLastCommitAgeBuildStrategyImpl_displayName();
        }
    }
}
