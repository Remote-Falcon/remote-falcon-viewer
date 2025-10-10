package com.remotefalcon.integration;

import com.remotefalcon.library.enums.LocationCheckMethod;
import com.remotefalcon.library.models.*;
import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.repository.ShowRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for voteForSequence mutation.
 * This test verifies the complete flow from GraphQL API to database persistence.
 * Uses Testcontainers to provide a MongoDB instance, eliminating the need for local MongoDB.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VoteForSequenceIntegrationTest {

  @Inject
  ShowRepository showRepository;

  private static final String TEST_SUBDOMAIN = "vote-integration-test-show";
  private static final String TEST_IP = "192.168.1.200";

  @BeforeAll
  static void setup() {
    RestAssured.basePath = "/remote-falcon-viewer";
  }

  @BeforeEach
  void setUp() {
    // Clean up any existing test data
    showRepository.findByShowSubdomain(TEST_SUBDOMAIN)
        .ifPresent(show -> showRepository.delete(show));

    // Create a test show with realistic configuration
    Show testShow = createTestShow();
    showRepository.persist(testShow);
  }

  @AfterEach
  void tearDown() {
    // Clean up test data after each test
    showRepository.findByShowSubdomain(TEST_SUBDOMAIN)
        .ifPresent(show -> showRepository.delete(show));
  }

  @Test
  @Order(1)
  @DisplayName("E2E: Successfully vote for sequence with valid data")
  void testVoteForSequence_Success() {
    String mutation = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Jingle Bells",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    // Execute GraphQL mutation
    Boolean result = given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", TEST_IP)
        .body(buildGraphQLRequest(mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.voteForSequence", is(true))
        .extract()
        .path("data.voteForSequence");

    assertTrue(result);

    // Verify database state
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();

    // Verify vote was added
    assertNotNull(updatedShow.getVotes(), "Votes list should not be null");
    assertEquals(1, updatedShow.getVotes().size(), "Should have exactly one vote");

    Vote vote = updatedShow.getVotes().get(0);
    assertEquals("Jingle Bells", vote.getSequence().getName());
    assertEquals(1, vote.getVotes());
    assertFalse(vote.getOwnerVoted());
    assertTrue(vote.getViewersVoted().contains(TEST_IP));
    assertNotNull(vote.getLastVoteTime());

    // Verify voting stat was added
    assertNotNull(updatedShow.getStats().getVoting(), "Voting stats should not be null");
    assertEquals(1, updatedShow.getStats().getVoting().size(), "Should have exactly one voting stat");
    assertEquals("Jingle Bells", updatedShow.getStats().getVoting().get(0).getName());
  }

  @Test
  @Order(2)
  @DisplayName("E2E: Increment existing vote for sequence")
  void testVoteForSequence_IncrementExisting() {
    // First vote
    String mutation1 = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Jingle Bells",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", "1.1.1.1")
        .body(buildGraphQLRequest(mutation1))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.voteForSequence", is(true));

    // Second vote from different IP
    String mutation2 = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Jingle Bells",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", "2.2.2.2")
        .body(buildGraphQLRequest(mutation2))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.voteForSequence", is(true));

    // Verify database state
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();

    assertEquals(1, updatedShow.getVotes().size(), "Should still have only one vote entry");

    Vote vote = updatedShow.getVotes().get(0);
    assertEquals(2, vote.getVotes(), "Vote count should be incremented to 2");
    assertEquals(2, vote.getViewersVoted().size(), "Should have 2 voter IPs");
    assertTrue(vote.getViewersVoted().contains("1.1.1.1"));
    assertTrue(vote.getViewersVoted().contains("2.2.2.2"));

    // Verify two voting stats were added
    assertEquals(2, updatedShow.getStats().getVoting().size(), "Should have two voting stats");
  }

  @Test
  @Order(3)
  @DisplayName("E2E: Successfully vote for sequence group")
  void testVoteForSequence_SequenceGroup() {
    String mutation = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Christmas Classics",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    // Execute GraphQL mutation
    Boolean result = given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", TEST_IP)
        .body(buildGraphQLRequest(mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.voteForSequence", is(true))
        .extract()
        .path("data.voteForSequence");

    assertTrue(result);

    // Verify database state
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();

    // Verify vote was added for the group
    assertEquals(1, updatedShow.getVotes().size());
    Vote vote = updatedShow.getVotes().get(0);
    assertNotNull(vote.getSequenceGroup(), "Vote should be for a sequence group");
    assertEquals("Christmas Classics", vote.getSequenceGroup().getName());
    assertEquals(1, vote.getVotes());
    assertTrue(vote.getViewersVoted().contains(TEST_IP));

    // Verify voting stat was added
    assertEquals(1, updatedShow.getStats().getVoting().size());
    assertEquals("Christmas Classics", updatedShow.getStats().getVoting().get(0).getName());
  }

  @Test
  @Order(4)
  @DisplayName("E2E: Fail to vote when viewer already voted")
  void testVoteForSequence_AlreadyVoted() {
    // Update show preferences to check if voted
    Show show = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    show.getPreferences().setCheckIfVoted(true);

    // Add existing vote from same IP
    Vote existingVote = Vote.builder()
        .sequence(show.getSequences().get(0))
        .ownerVoted(false)
        .viewersVoted(List.of(TEST_IP))
        .votes(1)
        .lastVoteTime(java.time.LocalDateTime.now())
        .build();
    show.getVotes().add(existingVote);
    showRepository.update(show);

    String mutation = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Silent Night",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    // Execute GraphQL mutation - should fail with ALREADY_VOTED
    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", TEST_IP)
        .body(buildGraphQLRequest(mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue())
        .body("errors[0].extensions.message", containsString("ALREADY_VOTED"));

    // Verify no new vote was added
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    assertEquals(1, updatedShow.getVotes().size(), "Should still have only one vote");
  }

  @Test
  @Order(5)
  @DisplayName("E2E: Fail to vote when viewer IP is blocked")
  void testVoteForSequence_BlockedIP() {
    // Update show preferences to block the test IP
    Show show = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    show.getPreferences().getBlockedViewerIps().add(TEST_IP);
    showRepository.update(show);

    String mutation = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Jingle Bells",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    // Execute GraphQL mutation - should fail with NAUGHTY
    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", TEST_IP)
        .body(buildGraphQLRequest(mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue())
        .body("errors[0].extensions.message", containsString("NAUGHTY"));

    // Verify no vote was added
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    assertEquals(0, updatedShow.getVotes().size(), "Should have no votes");
  }

  @Test
  @Order(6)
  @DisplayName("E2E: Fail to vote when location is invalid")
  void testVoteForSequence_InvalidLocation() {
    // Update show preferences to enable location checking with small radius
    Show show = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    show.getPreferences().setLocationCheckMethod(LocationCheckMethod.GEO);
    show.getPreferences().setShowLatitude(40.7128f);
    show.getPreferences().setShowLongitude(-74.0060f);
    show.getPreferences().setAllowedRadius(0.1f); // Very small radius (0.1 miles)
    showRepository.update(show);

    String mutation = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Jingle Bells",
            latitude: 51.5074,
            longitude: -0.1278
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    // Execute GraphQL mutation - should fail with INVALID_LOCATION
    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", TEST_IP)
        .body(buildGraphQLRequest(mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue())
        .body("errors[0].extensions.message", containsString("INVALID_LOCATION"));

    // Verify no vote was added
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    assertEquals(0, updatedShow.getVotes().size(), "Should have no votes");
  }

  @Test
  @Order(7)
  @DisplayName("E2E: Multiple viewers can vote for different sequences")
  void testVoteForSequence_MultipleSequences() {
    // Vote 1: Jingle Bells from IP 1
    String mutation1 = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Jingle Bells",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", "1.1.1.1")
        .body(buildGraphQLRequest(mutation1))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.voteForSequence", is(true));

    // Vote 2: Silent Night from IP 2
    String mutation2 = """
        mutation {
          voteForSequence(
            showSubdomain: "%s",
            name: "Silent Night",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", "2.2.2.2")
        .body(buildGraphQLRequest(mutation2))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("data.voteForSequence", is(true));

    // Verify database state
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();

    // Should have votes for two different sequences
    assertEquals(2, updatedShow.getVotes().size());

    // Verify each vote
    Vote vote1 = updatedShow.getVotes().stream()
        .filter(v -> v.getSequence().getName().equals("Jingle Bells"))
        .findFirst()
        .orElseThrow();
    assertEquals(1, vote1.getVotes());
    assertTrue(vote1.getViewersVoted().contains("1.1.1.1"));

    Vote vote2 = updatedShow.getVotes().stream()
        .filter(v -> v.getSequence().getName().equals("Silent Night"))
        .findFirst()
        .orElseThrow();
    assertEquals(1, vote2.getVotes());
    assertTrue(vote2.getViewersVoted().contains("2.2.2.2"));

    // Verify voting stats
    assertEquals(2, updatedShow.getStats().getVoting().size());
  }

  /**
   * Helper method to create a test show with realistic configuration
   */
  private Show createTestShow() {
    Show show = new Show();
    show.setShowSubdomain(TEST_SUBDOMAIN);
    show.setShowName("Vote Integration Test Show");
    show.setLastLoginIp("10.0.0.1"); // Different from test IP
    show.setPlayingNow("");
    show.setPlayingNext("");

    // Create preferences
    Preference preferences = new Preference();
    preferences.setCheckIfVoted(false);
    preferences.setLocationCheckMethod(LocationCheckMethod.NONE);
    preferences.setBlockedViewerIps(new java.util.HashSet<>());
    show.setPreferences(preferences);

    // Create sequences
    List<Sequence> sequences = new ArrayList<>();

    Sequence seq1 = new Sequence();
    seq1.setName("Jingle Bells");
    seq1.setDisplayName("Jingle Bells");
    seq1.setOrder(1);
    sequences.add(seq1);

    Sequence seq2 = new Sequence();
    seq2.setName("Silent Night");
    seq2.setDisplayName("Silent Night");
    seq2.setOrder(2);
    sequences.add(seq2);

    show.setSequences(sequences);

    // Create sequence group
    List<SequenceGroup> sequenceGroups = new ArrayList<>();
    SequenceGroup group = new SequenceGroup();
    group.setName("Christmas Classics");
    group.setVisibilityCount(0);
    sequenceGroups.add(group);
    show.setSequenceGroups(sequenceGroups);

    // Initialize empty collections
    show.setRequests(new ArrayList<>());
    show.setVotes(new ArrayList<>());
    show.setActiveViewers(new ArrayList<>());
    show.setPsaSequences(new ArrayList<>());

    // Initialize stats
    Stat stats = new Stat();
    stats.setPage(new ArrayList<>());
    stats.setJukebox(new ArrayList<>());
    stats.setVoting(new ArrayList<>());
    show.setStats(stats);

    return show;
  }

  /**
   * Helper method to build GraphQL request body
   */
  private String buildGraphQLRequest(String query) {
    return """
        {
          "query": "%s"
        }
        """.formatted(query.replace("\n", "\\n").replace("\"", "\\\""));
  }
}
