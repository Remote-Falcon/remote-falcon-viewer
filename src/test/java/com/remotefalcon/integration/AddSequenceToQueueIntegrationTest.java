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
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Test for addSequenceToQueue mutation.
 * This test verifies the complete flow from GraphQL API to database persistence.
 * Uses Testcontainers to provide a MongoDB instance, eliminating the need for local MongoDB.
 */
@QuarkusTest
@QuarkusTestResource(MongoTestResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AddSequenceToQueueIntegrationTest {

  @Inject
  ShowRepository showRepository;

  private static final String TEST_SUBDOMAIN = "integration-test-show";
  private static final String TEST_IP = "192.168.1.100";

  @BeforeAll
  static void setup() {
    // Configure RestAssured to use the correct base path
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
  @DisplayName("E2E: Successfully add sequence to queue with valid data")
  void testAddSequenceToQueue_Success() {
    String mutation = """
        mutation {
          addSequenceToQueue(
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
        .body("data.addSequenceToQueue", is(true))
        .extract()
        .path("data.addSequenceToQueue");

    assertTrue(result);

    // Verify database state
    Optional<Show> updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN);
    assertTrue(updatedShow.isPresent(), "Show should exist in database");

    Show show = updatedShow.get();

    // Verify request was added
    assertNotNull(show.getRequests(), "Requests list should not be null");
    assertEquals(1, show.getRequests().size(), "Should have exactly one request");

    Request request = show.getRequests().get(0);
    assertEquals("Jingle Bells", request.getSequence().getName());
    assertEquals(TEST_IP, request.getViewerRequested());
    assertFalse(request.getOwnerRequested());
    assertEquals(1, request.getPosition());

    // Verify jukebox stat was added
    assertNotNull(show.getStats().getJukebox(), "Jukebox stats should not be null");
    assertEquals(1, show.getStats().getJukebox().size(), "Should have exactly one jukebox stat");

    Stat.Jukebox jukeboxStat = show.getStats().getJukebox().get(0);
    assertEquals("Jingle Bells", jukeboxStat.getName());
    assertNotNull(jukeboxStat.getDateTime());
  }

  @Test
  @Order(2)
  @DisplayName("E2E: Fail to add sequence when queue is full")
  void testAddSequenceToQueue_QueueFull() {
    // First, update show preferences to set queue depth to 1
    Show show = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    show.getPreferences().setJukeboxDepth(1);

    // Add one request to fill the queue
    Request existingRequest = Request.builder()
        .sequence(show.getSequences().get(0))
        .ownerRequested(false)
        .viewerRequested("1.1.1.1")
        .position(1)
        .build();
    show.getRequests().add(existingRequest);
    showRepository.update(show);

    String mutation = """
        mutation {
          addSequenceToQueue(
            showSubdomain: "%s",
            name: "Silent Night",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    // Execute GraphQL mutation - should fail with QUEUE_FULL
    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", TEST_IP)
        .body(buildGraphQLRequest(mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue())
        .body("errors[0].extensions.message", containsString("QUEUE_FULL"));

    // Verify no new request was added
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    assertEquals(1, updatedShow.getRequests().size(), "Should still have only one request");
  }

  @Test
  @Order(3)
  @DisplayName("E2E: Fail to add sequence when viewer already requested")
  void testAddSequenceToQueue_AlreadyRequested() {
    // Update show preferences to check if requested
    Show show = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    show.getPreferences().setCheckIfRequested(true);

    // Add existing request from same IP
    Request existingRequest = Request.builder()
        .sequence(show.getSequences().get(0))
        .ownerRequested(false)
        .viewerRequested(TEST_IP)
        .position(1)
        .build();
    show.getRequests().add(existingRequest);
    showRepository.update(show);

    String mutation = """
        mutation {
          addSequenceToQueue(
            showSubdomain: "%s",
            name: "Silent Night",
            latitude: 40.7128,
            longitude: -74.0060
          )
        }
        """.formatted(TEST_SUBDOMAIN);

    // Execute GraphQL mutation - should fail with ALREADY_REQUESTED
    given()
        .contentType(ContentType.JSON)
        .header("CF-Connecting-IP", TEST_IP)
        .body(buildGraphQLRequest(mutation))
        .when()
        .post("/graphql")
        .then()
        .statusCode(200)
        .body("errors", notNullValue())
        .body("errors[0].extensions.message", containsString("ALREADY_REQUESTED"));

    // Verify no new request was added
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    assertEquals(1, updatedShow.getRequests().size(), "Should still have only one request");
  }

  @Test
  @Order(4)
  @DisplayName("E2E: Fail to add sequence when viewer is blocked")
  void testAddSequenceToQueue_BlockedIP() {
    // Update show preferences to block the test IP
    Show show = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    show.getPreferences().getBlockedViewerIps().add(TEST_IP);
    showRepository.update(show);

    String mutation = """
        mutation {
          addSequenceToQueue(
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

    // Verify no request was added
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    assertEquals(0, updatedShow.getRequests().size(), "Should have no requests");
  }

  @Test
  @Order(5)
  @DisplayName("E2E: Fail to add sequence when location is invalid")
  void testAddSequenceToQueue_InvalidLocation() {
    // Update show preferences to enable location checking with small radius
    Show show = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    show.getPreferences().setLocationCheckMethod(LocationCheckMethod.GEO);
    show.getPreferences().setShowLatitude(40.7128f);
    show.getPreferences().setShowLongitude(-74.0060f);
    show.getPreferences().setAllowedRadius(0.1f); // Very small radius (0.1 miles)
    showRepository.update(show);

    String mutation = """
        mutation {
          addSequenceToQueue(
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

    // Verify no request was added
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();
    assertEquals(0, updatedShow.getRequests().size(), "Should have no requests");
  }

  @Test
  @Order(6)
  @DisplayName("E2E: Successfully add sequence group to queue")
  void testAddSequenceToQueue_SequenceGroup() {
    String mutation = """
        mutation {
          addSequenceToQueue(
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
        .body("data.addSequenceToQueue", is(true))
        .extract()
        .path("data.addSequenceToQueue");

    assertTrue(result);

    // Verify database state
    Optional<Show> updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN);
    assertTrue(updatedShow.isPresent(), "Show should exist in database");

    Show show = updatedShow.get();

    // Verify all sequences in group were added
    assertNotNull(show.getRequests(), "Requests list should not be null");
    assertEquals(2, show.getRequests().size(), "Should have two requests (one for each sequence in group)");

    // Verify requests are in correct order
    assertEquals("Jingle Bells", show.getRequests().get(0).getSequence().getName());
    assertEquals("Silent Night", show.getRequests().get(1).getSequence().getName());

    // Verify all requests have correct viewer IP
    show.getRequests().forEach(request -> {
      assertEquals(TEST_IP, request.getViewerRequested());
      assertFalse(request.getOwnerRequested());
    });

    // Verify jukebox stat was added for the group
    assertNotNull(show.getStats().getJukebox(), "Jukebox stats should not be null");
    assertEquals(1, show.getStats().getJukebox().size(), "Should have exactly one jukebox stat for the group");
    assertEquals("Christmas Classics", show.getStats().getJukebox().get(0).getName());
  }

  @Test
  @Order(7)
  @DisplayName("E2E: Multiple concurrent requests maintain correct position ordering")
  void testAddSequenceToQueue_ConcurrentRequests() {
    // Add first request
    String mutation1 = """
        mutation {
          addSequenceToQueue(
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
        .body("data.addSequenceToQueue", is(true));

    // Add second request from different IP
    String mutation2 = """
        mutation {
          addSequenceToQueue(
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
        .body("data.addSequenceToQueue", is(true));

    // Verify database state
    Show updatedShow = showRepository.findByShowSubdomain(TEST_SUBDOMAIN).orElseThrow();

    // Verify requests maintain correct ordering
    assertEquals(2, updatedShow.getRequests().size());
    assertEquals(1, updatedShow.getRequests().get(0).getPosition());
    assertEquals(2, updatedShow.getRequests().get(1).getPosition());

    // Verify correct IPs
    assertEquals("1.1.1.1", updatedShow.getRequests().get(0).getViewerRequested());
    assertEquals("2.2.2.2", updatedShow.getRequests().get(1).getViewerRequested());
  }

  /**
   * Helper method to create a test show with realistic configuration
   */
  private Show createTestShow() {
    Show show = new Show();
    show.setShowSubdomain(TEST_SUBDOMAIN);
    show.setShowName("Integration Test Show");
    show.setLastLoginIp("10.0.0.1"); // Different from test IP
    show.setPlayingNow("");
    show.setPlayingNext("");

    // Create preferences
    Preference preferences = new Preference();
    preferences.setJukeboxDepth(0); // Unlimited by default
    preferences.setCheckIfRequested(false);
    preferences.setCheckIfVoted(false);
    preferences.setLocationCheckMethod(LocationCheckMethod.NONE);
    preferences.setJukeboxRequestLimit(0);
    preferences.setPsaEnabled(false);
    preferences.setManagePsa(false);
    preferences.setBlockedViewerIps(new java.util.HashSet<>());
    show.setPreferences(preferences);

    // Create sequences
    List<Sequence> sequences = new ArrayList<>();

    Sequence seq1 = new Sequence();
    seq1.setName("Jingle Bells");
    seq1.setDisplayName("Jingle Bells");
    seq1.setGroup("Christmas Classics");
    seq1.setOrder(1);
    sequences.add(seq1);

    Sequence seq2 = new Sequence();
    seq2.setName("Silent Night");
    seq2.setDisplayName("Silent Night");
    seq2.setGroup("Christmas Classics");
    seq2.setOrder(2);
    sequences.add(seq2);

    show.setSequences(sequences);

    // Create sequence groups
    List<SequenceGroup> sequenceGroups = new ArrayList<>();
    SequenceGroup group = new SequenceGroup();
    group.setName("Christmas Classics");
    group.setVisibilityCount(2);
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
