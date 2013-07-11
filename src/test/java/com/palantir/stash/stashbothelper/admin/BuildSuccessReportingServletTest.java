package com.palantir.stash.stashbothelper.admin;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.atlassian.stash.build.BuildStatus;
import com.atlassian.stash.build.BuildStatus.State;
import com.atlassian.stash.build.BuildStatusService;
import com.atlassian.stash.project.Project;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.pull.PullRequestService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.repository.RepositoryService;
import com.palantir.stash.stashbothelper.config.ConfigurationPersistenceManager;
import com.palantir.stash.stashbothelper.config.JenkinsServerConfiguration;
import com.palantir.stash.stashbothelper.config.RepositoryConfiguration;
import com.palantir.stash.stashbothelper.managers.JenkinsBuildTypes;

public class BuildSuccessReportingServletTest {

    private static final String HEAD = "38356e8abe0e97648dd1007278ecc02c3bf3d2cb";
    private static final String MERGE_HEAD = "cac9954e06013073c1bf9e17b2c1c919095817dc";
    private static final State SUCCESSFUL = State.SUCCESSFUL;
    private static final State INPROGRESS = State.INPROGRESS;
    private static final State FAILED = State.FAILED;
    private static final long BUILD_NUMBER = 12345L;
    private static final int REPO_ID = 1;
    private static final long PULL_REQUEST_ID = 1234L;

    @Mock
    private ConfigurationPersistenceManager cpm;
    @Mock
    private RepositoryService repositoryService;
    @Mock
    private BuildStatusService bss;
    @Mock
    private PullRequestService prs;
    @Mock
    private RepositoryConfiguration rc;
    @Mock
    private JenkinsServerConfiguration jsc;

    @Mock
    private HttpServletRequest req;
    @Mock
    private HttpServletResponse res;
    @Mock
    private Repository repo;
    @Mock
    private Project proj;
    @Mock
    private PullRequest pr;

    private StringWriter mockWriter;

    private BuildSuccessReportingServlet bsrs;

    @Before
    public void setUp() throws IOException, SQLException {
        MockitoAnnotations.initMocks(this);

        Mockito.when(cpm.getJenkinsServerConfiguration()).thenReturn(jsc);
        Mockito.when(cpm.getRepositoryConfigurationForRepository(Mockito.any(Repository.class))).thenReturn(rc);
        Mockito.when(repositoryService.getById(REPO_ID)).thenReturn(repo);
        Mockito.when(prs.findById(REPO_ID, PULL_REQUEST_ID)).thenReturn(pr);
        Mockito.when(pr.getId()).thenReturn(PULL_REQUEST_ID);
        Mockito.when(repo.getId()).thenReturn(REPO_ID);
        Mockito.when(repo.getSlug()).thenReturn("slug");
        Mockito.when(repo.getProject()).thenReturn(proj);
        Mockito.when(proj.getKey()).thenReturn("projectKey");

        mockWriter = new StringWriter();
        Mockito.when(res.getWriter()).thenReturn(new PrintWriter(mockWriter));

        bsrs = new BuildSuccessReportingServlet(cpm, repositoryService, bss, prs);
    }

    @Test
    public void testReportingSuccess() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, SUCCESSFUL, BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatus> buildStatusCaptor = ArgumentCaptor.forClass(BuildStatus.class);

        Mockito.verify(bss).add(Mockito.eq(HEAD), buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatus bs = buildStatusCaptor.getValue();
        Assert.assertEquals(bs.getState(), SUCCESSFUL);
        Assert.assertEquals(bs.getKey(), JenkinsBuildTypes.VERIFICATION.getBuildNameFor(repo));
        Assert.assertEquals(bs.getName(), JenkinsBuildTypes.VERIFICATION.toString());
    }

    @Test
    public void testReportingInprogress() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, INPROGRESS, BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatus> buildStatusCaptor = ArgumentCaptor.forClass(BuildStatus.class);

        Mockito.verify(bss).add(Mockito.eq(HEAD), buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatus bs = buildStatusCaptor.getValue();
        Assert.assertEquals(bs.getState(), INPROGRESS);
        Assert.assertEquals(bs.getKey(), JenkinsBuildTypes.VERIFICATION.getBuildNameFor(repo));
        Assert.assertEquals(bs.getName(), JenkinsBuildTypes.VERIFICATION.toString());
    }

    @Test
    public void testReportingFailure() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, FAILED, BUILD_NUMBER, HEAD, null, null));

        bsrs.doGet(req, res);

        ArgumentCaptor<BuildStatus> buildStatusCaptor = ArgumentCaptor.forClass(BuildStatus.class);

        Mockito.verify(bss).add(Mockito.eq(HEAD), buildStatusCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        BuildStatus bs = buildStatusCaptor.getValue();
        Assert.assertEquals(bs.getState(), FAILED);
        Assert.assertEquals(bs.getKey(), JenkinsBuildTypes.VERIFICATION.getBuildNameFor(repo));
        Assert.assertEquals(bs.getName(), JenkinsBuildTypes.VERIFICATION.toString());
    }

    @Test
    public void testMergeBuildReportingSuccess() throws ServletException, IOException {
        Mockito.when(req.getPathInfo()).thenReturn(
            buildPathInfo(REPO_ID, JenkinsBuildTypes.VERIFICATION, SUCCESSFUL, BUILD_NUMBER, HEAD, MERGE_HEAD,
                PULL_REQUEST_ID));

        bsrs.doGet(req, res);

        ArgumentCaptor<String> stringCaptor = ArgumentCaptor.forClass(String.class);

        Mockito.verify(prs).addComment(Mockito.eq(REPO_ID), Mockito.eq(PULL_REQUEST_ID), stringCaptor.capture());
        Mockito.verify(res).setStatus(200);

        String output = mockWriter.toString();
        Assert.assertTrue(output.contains("Status Updated"));

        String commentText = stringCaptor.getValue();
        Assert.assertTrue(commentText.contains("==SUCCESSFUL=="));
    }

    // path info: "/BASE_URL/REPO_ID/TYPE/STATE/BUILD_NUMBER/BUILD_HEAD[/MERGE_HEAD/PULLREQUEST_ID]"
    private String buildPathInfo(int repoId, JenkinsBuildTypes type, State state, long buildNumber, String head,
        String mergeHead, Long pullRequestId) {
        return "/"
            + Integer.toString(repoId) + "/"
            + type.toString() + "/"
            + state.toString() + "/"
            + Long.toString(buildNumber) + "/"
            + head + "/"
            + (mergeHead != null ? mergeHead : "") + "/"
            + (pullRequestId != null ? pullRequestId.toString() : "");
    }
}