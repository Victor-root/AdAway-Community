package org.adaway.model.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.net.MalformedURLException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;

/**
 * This class tests the git hosted source behavior.
 * <p>
 * It checks the hosting detection, the API URL each hosting builds from a hosts file URL and the
 * parsing of a recorded API response, without any network access: the HTTP call in between is
 * plain OkHttp.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
@RunWith(Parameterized.class)
public class GitHostsSourceTest {

    private static final String GITHUB_HOST = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    private static final String GIST_HOST = "https://gist.githubusercontent.com/PerfectSlayer/a552900539d10271542063d67424b467/raw/56aabad791fbd085f4b9c5051a1dfa76b9a9d748/hosts";
    private static final String GITLAB_HOST = "https://gitlab.com/quidsup/notrack-blocklists/raw/master/notrack-blocklist.txt";

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {
                        "GitHub",
                        GITHUB_HOST,
                        GitHubHostsSource.class,
                        "https://api.github.com/repos/StevenBlack/hosts/commits?per_page=1&path=hosts",
                        "[{\"sha\":\"abc\",\"commit\":{\"committer\":{\"name\":\"Steven Black\",\"date\":\"2024-05-04T21:32:10Z\"}}}]",
                        "2024-05-04T21:32:10Z"
                },
                {
                        "Gist",
                        GIST_HOST,
                        GistHostsSource.class,
                        "https://api.github.com/gists/a552900539d10271542063d67424b467",
                        "{\"id\":\"a552900539d10271542063d67424b467\",\"updated_at\":\"2018-03-11T16:23:42Z\"}",
                        "2018-03-11T16:23:42Z"
                },
                {
                        "GitLab",
                        GITLAB_HOST,
                        GitLabHostsSource.class,
                        "https://gitlab.com/api/v4/projects/quidsup%2Fnotrack-blocklists/repository/commits?path=notrack-blocklist.txt&ref_name=master",
                        "[{\"id\":\"abc\",\"committed_date\":\"2023-11-02T08:15:30.000+00:00\"}]",
                        "2023-11-02T08:15:30Z"
                }
        });
    }

    private final String label;
    private final String url;
    private final Class<?> expectedClass;
    private final String expectedApiUrl;
    private final String apiResponse;
    private final ZonedDateTime expectedLastUpdate;

    /**
     * Constructor.
     *
     * @param label              The Git hosting label.
     * @param url                A git hosted file URL.
     * @param expectedClass      The expected git hosting strategy.
     * @param expectedApiUrl     The expected API URL to get the file last commit.
     * @param apiResponse        A recorded API response for the file last commit.
     * @param expectedLastUpdate The last update date in the recorded API response.
     */
    public GitHostsSourceTest(String label, String url, Class<?> expectedClass, String expectedApiUrl,
                              String apiResponse, String expectedLastUpdate) {
        this.label = label;
        this.url = url;
        this.expectedClass = expectedClass;
        this.expectedApiUrl = expectedApiUrl;
        this.apiResponse = apiResponse;
        this.expectedLastUpdate = ZonedDateTime.parse(expectedLastUpdate);
    }

    /**
     * Check the source is well detected as git hasted.
     */
    @Test
    public void testIsHostedOnGit() {
        assertTrue(
                "Git hosting for " + this.label + " was not detected",
                GitHostsSource.isHostedOnGit(this.url)
        );
    }

    /**
     * Check the last update date is read from the right API with the right format.
     */
    @Test
    public void testLastUpdateParsing() throws MalformedURLException, JSONException {
        GitHostsSource source = GitHostsSource.getSource(this.url);
        assertTrue(
                "Invalid git hosting strategy for " + this.label,
                this.expectedClass.isInstance(source)
        );
        GitHostsJsonApiSource apiSource = (GitHostsJsonApiSource) source;
        assertEquals(
                "Invalid commit API URL for " + this.label,
                this.expectedApiUrl,
                apiSource.getCommitApiUrl()
        );
        ZonedDateTime lastUpdate = apiSource.parseJsonBody(this.apiResponse);
        assertEquals(
                "Failed to read last modified date of " + this.label + " host file",
                this.expectedLastUpdate.toInstant(),
                lastUpdate.toInstant()
        );
    }
}
