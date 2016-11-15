package jetbrains.buildServer.commitPublisher;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.*;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public class CommitStatusPublisherListenerTest extends CommitStatusPublisherTestBase {

  private CommitStatusPublisherListener myListener;
  private MockPublisherRegisterFailure myPublisher;
  private VcsRootInstance myVcsRootInstance;

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    final PublisherManager myPublisherManager = new PublisherManager(Collections.<CommitStatusPublisherSettings>singletonList(new CommitStatusPublisherListenerTest.MockPublisherSettings()));
    final BuildHistory history = myFixture.getHistory();
    myListener = new CommitStatusPublisherListener(EventDispatcher.create(BuildServerListener.class), myPublisherManager, history, myRBManager, myProblems);
    myPublisher = new MockPublisherRegisterFailure(myBuildType, myProblems);
  }

  public void should_publish_failure() {
    prepareGoodVcs();
    myRunningBuild = myFixture.startBuild(myBuildType);
    myListener.buildChangedStatus(myRunningBuild, Status.NORMAL, Status.FAILURE);
    then(myPublisher.isFailureReceived()).isTrue();
  }

  public void should_publish_finished_success() {
    prepareGoodVcs();
    myRunningBuild = myFixture.startBuild(myBuildType);
    myFixture.finishBuild(myRunningBuild, false);
    myListener.buildFinished(myRunningBuild);
    then(myPublisher.isFinishedReceived()).isTrue();
    then(myPublisher.isSuccessReceived()).isTrue();
  }


  public void should_not_publish_failure_if_marked_successful() {
    prepareGoodVcs();
    myRunningBuild = myFixture.startBuild(myBuildType);
    myListener.buildChangedStatus(myRunningBuild, Status.FAILURE, Status.NORMAL);
    then(myPublisher.isFailureReceived()).isFalse();
  }

  public void should_not_publish_if_failed_to_collect_changes() {
    prepareBadVcs();
    myRunningBuild = myFixture.startBuild(myBuildType);
    myListener.buildChangedStatus(myRunningBuild, Status.NORMAL, Status.FAILURE);
    then(myPublisher.isFailureReceived()).isFalse();
  }

  // temporary disabled until TW-47724 is fixed
  private void should_not_publish_status_for_personal_builds() throws IOException {
    prepareGoodVcs();
    myRunningBuild = myFixture.startPersonalBuild(myFixture.createUserAccount("newuser"), myBuildType);
    myFixture.finishBuild(myRunningBuild, false);
    myListener.buildFinished(myRunningBuild);
    then(myPublisher.isFinishedReceived()).isFalse();
    then(myPublisher.isSuccessReceived()).isFalse();
  }

  private void prepareGoodVcs() {
    final SVcsRoot vcsRoot = myFixture.addVcsRoot("svn", "vcs1");
    myPublisher.setVcsRootId(vcsRoot.getExternalId());
    myBuildType.addVcsRoot(vcsRoot);
    myVcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
    myCurrentVersion = "111";
    myFixture.addModification(new ModificationData(new Date(),
            Collections.singletonList(new VcsChange(VcsChangeInfo.Type.CHANGED, "changed", "file", "file","1", "2")),
            "descr2", "user", myVcsRootInstance, "rev2", "rev2"));
  }

  private void prepareBadVcs() {
    final SVcsRoot vcsRoot = myFixture.addVcsRoot("failing", "vcs2");
    myPublisher.setVcsRootId(vcsRoot.getExternalId());
    myBuildType.addVcsRoot(vcsRoot);
    myVcsRootInstance = myBuildType.getVcsRootInstances().iterator().next();
  }

  private class MockPublisherRegisterFailure extends MockPublisher {

    private boolean myFailureReceived = false;
    private boolean myFinishedReceived = false;
    private boolean mySuccessReceived = false;

    MockPublisherRegisterFailure(SBuildType buildType, CommitStatusPublisherProblems problems) {
      super(PUBLISHER_ID, buildType, myFeatureDescriptor.getId(), Collections.<String, String>emptyMap(), problems);
    }

    boolean isFailureReceived() { return myFailureReceived; }
    boolean isFinishedReceived() { return myFinishedReceived; }
    boolean isSuccessReceived() { return mySuccessReceived; }

    @Override
    public boolean buildFinished(@NotNull SFinishedBuild build, @NotNull BuildRevision revision) {
      myFinishedReceived = true;
      Status s = build.getBuildStatus();
      if (s.equals(Status.NORMAL)) mySuccessReceived = true;
      if (s.equals(Status.FAILURE)) myFailureReceived = true;
      return true;
    }

    @Override
    public boolean buildFailureDetected(@NotNull SRunningBuild build, @NotNull BuildRevision revision) {
      myFailureReceived = true;
      return true;
    }

    @Override
    public boolean buildMarkedAsSuccessful(@NotNull SBuild build, @NotNull BuildRevision revision, boolean buildInProgress) {
      return super.buildMarkedAsSuccessful(build, revision, buildInProgress);
    }

  }


  private class MockPublisherSettings extends DummyPublisherSettings {
    @Override
    @NotNull
    public String getId() {
      return PUBLISHER_ID;
    }

    @Override
    public CommitStatusPublisher createPublisher(@NotNull SBuildType buildType, @NotNull String buildFeatureId, @NotNull Map<String, String> params) {
      return myPublisher;
    }
  }
}
