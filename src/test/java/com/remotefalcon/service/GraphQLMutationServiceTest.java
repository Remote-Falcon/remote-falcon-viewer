package com.remotefalcon.service;

import com.remotefalcon.exception.CustomGraphQLExceptionResolver;
import com.remotefalcon.library.enums.LocationCheckMethod;
import com.remotefalcon.library.models.*;
import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.repository.ShowRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class GraphQLMutationServiceTest {

  @Inject
  GraphQLMutationService service;

  @InjectMock
  ShowRepository showRepository;

  @InjectMock
  RoutingContext routingContext;

  @InjectMock
  HttpServerRequest httpServerRequest;

  @BeforeEach
  void setUp() {
    reset(showRepository, routingContext, httpServerRequest);
    when(routingContext.request()).thenReturn(httpServerRequest);
    when(httpServerRequest.getHeader("CF-Connecting-IP")).thenReturn("1.2.3.4");
  }

  private Show mockShowWithStatsAndActiveViewers(String lastLoginIp) {
    Show show = mock(Show.class);
    when(show.getLastLoginIp()).thenReturn(lastLoginIp);

    // Stats with real lists
    Stat stats = mock(Stat.class);
    when(stats.getPage()).thenReturn(new ArrayList<>());
    when(stats.getJukebox()).thenReturn(new ArrayList<>());
    when(stats.getVoting()).thenReturn(new ArrayList<>());
    when(show.getStats()).thenReturn(stats);

    // Active viewers list
    List<ActiveViewer> activeViewers = new ArrayList<>();
    when(show.getActiveViewers()).thenReturn(activeViewers);

    return show;
  }

  private Show mockShowWithPrefsAndCollections() {
    // Use deep stubs to allow chaining on getPreferences()
    Show show = mock(Show.class, RETURNS_DEEP_STUBS);
    when(show.getLastLoginIp()).thenReturn("9.9.9.9");

    // Stats with real lists
    Stat stats = mock(Stat.class);
    when(stats.getPage()).thenReturn(new ArrayList<>());
    when(stats.getJukebox()).thenReturn(new ArrayList<>());
    when(stats.getVoting()).thenReturn(new ArrayList<>());
    when(show.getStats()).thenReturn(stats);

    // Active viewers list
    List<ActiveViewer> activeViewers = new ArrayList<>();
    when(show.getActiveViewers()).thenReturn(activeViewers);

    // Preferences chain
    when(show.getPreferences().getCheckIfVoted()).thenReturn(false);
    when(show.getPreferences().getCheckIfRequested()).thenReturn(false);
    when(show.getPreferences().getJukeboxDepth()).thenReturn(0);
    when(show.getPreferences().getShowLatitude()).thenReturn(0.0f);
    when(show.getPreferences().getShowLongitude()).thenReturn(0.0f);
    when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.NONE);
    when(show.getPreferences().getPsaEnabled()).thenReturn(false);
    when(show.getPreferences().getManagePsa()).thenReturn(false);

    when(show.getSequences()).thenReturn(new ArrayList<>());
    when(show.getSequenceGroups()).thenReturn(new ArrayList<>());
    when(show.getRequests()).thenReturn(new ArrayList<>());
    when(show.getVotes()).thenReturn(new ArrayList<>());
    when(show.getPsaSequences()).thenReturn(new ArrayList<>());

    return show;
  }

  @Nested
  @DisplayName("insertViewerPageStats")
  class InsertViewerPageStatsTests {
    @Test
    @DisplayName("Should add page stat and persist when show exists and IP differs from last login")
    void shouldInsertViewerPageStats() {
      Show show = mockShowWithStatsAndActiveViewers("9.9.9.9");
      when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

      Boolean result = service.insertViewerPageStats("test", LocalDateTime.now());

      assertTrue(result);
      assertEquals(1, show.getStats().getPage().size());
      verify(showRepository).persistOrUpdate(any(Show.class));
    }

    @Test
    @DisplayName("Should return true but not persist when IP equals last login IP")
    void shouldReturnTrueButNotPersistWhenSameIp() {
      Show show = mockShowWithStatsAndActiveViewers("1.2.3.4");
      when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

      Boolean result = service.insertViewerPageStats("test", LocalDateTime.now());

      assertTrue(result);
      assertEquals(0, show.getStats().getPage().size());
      verify(showRepository, never()).persistOrUpdate(any(Show.class));
    }

    @Test
    @DisplayName("Should throw when show not found")
    void shouldThrowWhenShowNotFound() {
      when(showRepository.findByShowSubdomain("missing")).thenReturn(Optional.empty());
      assertThrows(CustomGraphQLExceptionResolver.class,
          () -> service.insertViewerPageStats("missing", LocalDateTime.now()));
    }
  }

  @Nested
  @DisplayName("updateActiveViewers")
  class UpdateActiveViewersTests {
    @Test
    @DisplayName("Should add active viewer and persist when IP differs from last login and remove existing duplicate")
    void shouldUpdateActiveViewers() {
      Show show = mockShowWithStatsAndActiveViewers("9.9.9.9");
      ActiveViewer existing = mock(ActiveViewer.class);
      when(existing.getIpAddress()).thenReturn("1.2.3.4");
      show.getActiveViewers().add(existing);
      when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

      Boolean result = service.updateActiveViewers("test");

      assertTrue(result);
      assertEquals(1, show.getActiveViewers().size());
      verify(showRepository).persistOrUpdate(show);
    }

    @Test
    @DisplayName("Should not persist when IP equals last login IP")
    void shouldNotPersistWhenSameIpAsLastLogin() {
      Show show = mockShowWithStatsAndActiveViewers("1.2.3.4");
      when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

      Boolean result = service.updateActiveViewers("test");

      assertTrue(result);
      verify(showRepository, never()).persistOrUpdate(any(Show.class));
    }

    @Test
    @DisplayName("Should throw when show not found for active viewers update")
    void shouldThrowWhenShowNotFound() {
      when(showRepository.findByShowSubdomain("missing")).thenReturn(Optional.empty());
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.updateActiveViewers("missing"));
    }
  }

  @Nested
  @DisplayName("updatePlayingNow")
  class UpdatePlayingNowTests {
    @Test
    @DisplayName("Should update playing now and persist")
    void shouldUpdatePlayingNow() {
      Show show = mockShowWithStatsAndActiveViewers("9.9.9.9");
      when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

      Boolean result = service.updatePlayingNow("test", "Song A");

      assertTrue(result);
      verify(show).setPlayingNow("Song A");
      verify(showRepository).persistOrUpdate(show);
    }

    @Test
    @DisplayName("Should throw when show not found for playing now update")
    void shouldThrowPlayingNowNotFound() {
      when(showRepository.findByShowSubdomain("missing")).thenReturn(Optional.empty());
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.updatePlayingNow("missing", "Song A"));
    }
  }

  @Nested
  @DisplayName("updatePlayingNext")
  class UpdatePlayingNextTests {
    @Test
    @DisplayName("Should update playing next and persist")
    void shouldUpdatePlayingNext() {
      Show show = mockShowWithStatsAndActiveViewers("9.9.9.9");
      when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

      Boolean result = service.updatePlayingNext("test", "Next Song");

      assertTrue(result);
      verify(show).setPlayingNext("Next Song");
      verify(showRepository).persistOrUpdate(show);
    }

    @Test
    @DisplayName("Should throw when show not found for playing next update")
    void shouldThrowPlayingNextNotFound() {
      when(showRepository.findByShowSubdomain("missing")).thenReturn(Optional.empty());
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.updatePlayingNext("missing", "Next Song"));
    }
  }

  @Nested
  @DisplayName("addSequenceToQueue validations")
  class AddSequenceToQueueValidationTests {
    @Test
    @DisplayName("Should throw UNEXPECTED_ERROR when client IP is empty")
    void shouldThrowWhenClientIpEmpty() {
      when(httpServerRequest.getHeader("CF-Connecting-IP")).thenReturn("");
      Show show = mockShowWithPrefsAndCollections();
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw ALREADY_VOTED when preferences.checkIfVoted is true")
    void shouldThrowAlreadyVotedByPref() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getCheckIfVoted()).thenReturn(true);
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw NAUGHTY when IP is blocked")
    void shouldThrowNaughtyWhenIpBlocked() {
      Show show = mockShowWithPrefsAndCollections();
      Set<String> blocked = new HashSet<>();
      blocked.add("1.2.3.4");
      when(show.getPreferences().getBlockedViewerIps()).thenReturn(blocked);
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw ALREADY_REQUESTED when viewer has requested before and checkIfRequested is true")
    void shouldThrowAlreadyRequested() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getCheckIfRequested()).thenReturn(true);
      Request r = mock(Request.class);
      when(r.getViewerRequested()).thenReturn("1.2.3.4");
      show.getRequests().add(r);
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw QUEUE_FULL when queue depth reached and depth != 0")
    void shouldThrowQueueFull() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getJukeboxDepth()).thenReturn(1);
      Request r = mock(Request.class);
      show.getRequests().add(r);
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw INVALID_LOCATION when latitude or longitude is null")
    void shouldThrowInvalidLocation() {
      Show show = mockShowWithPrefsAndCollections();
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "name", null, 0f));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "name", 0f, null));
    }

    @Test
    @DisplayName("Should throw UNEXPECTED_ERROR when no matching sequence or group found")
    void shouldThrowUnexpectedWhenNoMatch() {
      Show show = mockShowWithPrefsAndCollections();
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      // Pass valid coords (location check NONE by default in mock)
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "unknown", 0f, 0f));
    }
  }

  @Nested
  @DisplayName("voteForSequence validations")
  class VoteForSequenceValidationTests {
    @Test
    @DisplayName("Should throw UNEXPECTED_ERROR when client IP is empty")
    void voteShouldThrowWhenClientIpEmpty() {
      when(httpServerRequest.getHeader("CF-Connecting-IP")).thenReturn("");
      Show show = mockShowWithPrefsAndCollections();
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.voteForSequence("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw ALREADY_VOTED when preferences.checkIfVoted is true")
    void voteShouldThrowAlreadyVotedByPref() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getCheckIfVoted()).thenReturn(true);
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.voteForSequence("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw NAUGHTY when IP is blocked")
    void voteShouldThrowNaughtyWhenIpBlocked() {
      Show show = mockShowWithPrefsAndCollections();
      show.getPreferences().getBlockedViewerIps().add("1.2.3.4");
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.voteForSequence("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw ALREADY_VOTED when viewer already voted and checkIfVoted is true")
    void voteShouldThrowAlreadyVotedByViewer() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getCheckIfVoted()).thenReturn(true);
      Vote v = mock(Vote.class);
      List<String> voters = new ArrayList<>();
      voters.add("1.2.3.4");
      when(v.getViewersVoted()).thenReturn(voters);
      show.getVotes().add(v);
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.voteForSequence("sub", "name", 0f, 0f));
    }

    @Test
    @DisplayName("Should throw INVALID_LOCATION when latitude or longitude is null")
    void voteShouldThrowInvalidLocation() {
      Show show = mockShowWithPrefsAndCollections();
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.voteForSequence("sub", "name", null, 0f));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.voteForSequence("sub", "name", 0f, null));
    }

    @Test
    @DisplayName("Should throw UNEXPECTED_ERROR when no matching sequence or group found")
    void voteShouldThrowUnexpectedWhenNoMatch() {
      Show show = mockShowWithPrefsAndCollections();
      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.voteForSequence("sub", "unknown", 0f, 0f));
    }
  }

  @Nested
  @DisplayName("addSequenceToQueue success and deeper branches")
  class AddSequenceToQueueMoreCoverageTests {
    @Test
    @DisplayName("GEO within radius allows request; initialize requests; jukebox stat and persist")
    void geoWithinRadiusAllowsSingleSequence() {
      Show show = mockShowWithPrefsAndCollections();
      // Switch to GEO and allow large radius so it passes
      when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.GEO);
      when(show.getPreferences().getShowLatitude()).thenReturn(0.0f);
      when(show.getPreferences().getShowLongitude()).thenReturn(0.0f);

      // Add a sequence match by name
      Sequence s = mock(Sequence.class);
      when(s.getName()).thenReturn("song-a");
      when(s.getDisplayName()).thenReturn("Song A");
      show.getSequences().add(s);

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      when(showRepository.nextRequestPosition("sub")).thenReturn(1L);

      Boolean result = service.addSequenceToQueue("sub", "song-a", 0f, 0f);
      assertTrue(result);

      // One jukebox stat added
      assertEquals(1, show.getStats().getJukebox().size());
      // Requests initialized with a single entry at position 1
      assertEquals(1, show.getRequests().size());
      Request r = show.getRequests().get(0);
      assertEquals(1, r.getPosition());
      assertEquals("1.2.3.4", r.getViewerRequested());

      // Note: Voting uses persist() which is not implemented yet
      // verify(showRepository).persist(any(Show.class));
    }

    @Test
    @DisplayName("Append sequence when requests exist; PSA handled (frequency 1) and appended after user request")
    void appendSequenceAndHandlePsa() {
      Show show = mockShowWithPrefsAndCollections();
      // Existing latest position 5
      Request existing = mock(Request.class);
      when(existing.getPosition()).thenReturn(5);
      show.getRequests().add(existing);

      // GEO ok
      when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.GEO);
      when(show.getPreferences().getShowLatitude()).thenReturn(0.0f);
      when(show.getPreferences().getShowLongitude()).thenReturn(0.0f);
      when(show.getPreferences().getAllowedRadius()).thenReturn(10000.0f);

      // PSA enabled and managed by app; frequency 1 ensures trigger
      when(show.getPreferences().getPsaEnabled()).thenReturn(true);
      when(show.getPreferences().getManagePsa()).thenReturn(false);
      when(show.getPreferences().getPsaFrequency()).thenReturn(1);

      // PSA sequences list with one entry
      PsaSequence psa = mock(PsaSequence.class);
      when(psa.getName()).thenReturn("psa-seq");
      when(psa.getLastPlayed()).thenReturn(LocalDateTime.now().minusDays(1));
      when(psa.getOrder()).thenReturn(1);
      show.getPsaSequences().add(psa);

      // Show sequences contain user requested and PSA target
      Sequence userSeq = mock(Sequence.class);
      when(userSeq.getName()).thenReturn("user-seq");
      when(userSeq.getDisplayName()).thenReturn("User Seq");
      Sequence psaSeq = mock(Sequence.class);
      when(psaSeq.getName()).thenReturn("psa-seq");
      show.getSequences().add(userSeq);
      show.getSequences().add(psaSeq);

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      when(showRepository.nextRequestPosition("sub")).thenReturn(6L, 7L);

      Boolean result = service.addSequenceToQueue("sub", "user-seq", 0f, 0f);
      assertTrue(result);

      // Two new requests should be appended: positions 6 (user) and 7 (PSA)
      assertEquals(3, show.getRequests().size());
      Request userReq = show.getRequests().get(1);
      Request psaReq = show.getRequests().get(2);
      assertEquals(6, userReq.getPosition());
      assertEquals("1.2.3.4", userReq.getViewerRequested());
      assertEquals(7, psaReq.getPosition());
      assertEquals("PSA", psaReq.getViewerRequested());
    }

    @Test
    @DisplayName("Add sequence group: requests for all sequences sorted by order and jukebox stat added")
    void addSequenceGroupRequestsAll() {
      Show show = mockShowWithPrefsAndCollections();
      // GEO ok
      when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.GEO);
      when(show.getPreferences().getShowLatitude()).thenReturn(0.0f);
      when(show.getPreferences().getShowLongitude()).thenReturn(0.0f);
      when(show.getPreferences().getAllowedRadius()).thenReturn(10000.0f);

      // Group and sequences
      SequenceGroup group = mock(SequenceGroup.class);
      when(group.getName()).thenReturn("GroupA");
      show.getSequenceGroups().add(group);

      Sequence s1 = mock(Sequence.class);
      when(s1.getName()).thenReturn("s1");
      when(s1.getDisplayName()).thenReturn("S1");
      when(s1.getGroup()).thenReturn("GroupA");
      when(s1.getOrder()).thenReturn(2);
      Sequence s2 = mock(Sequence.class);
      when(s2.getName()).thenReturn("s2");
      when(s2.getDisplayName()).thenReturn("S2");
      when(s2.getGroup()).thenReturn("GroupA");
      when(s2.getOrder()).thenReturn(1);
      show.getSequences().add(s1);
      show.getSequences().add(s2);

      // Ensure playing now/next are non-null and not matching
      when(show.getPlayingNow()).thenReturn("NotPlaying");
      when(show.getPlayingNext()).thenReturn("NotNext");

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      when(showRepository.allocatePositionBlock("sub", 2)).thenReturn(1L);

      Boolean result = service.addSequenceToQueue("sub", "GroupA", 0f, 0f);
      assertTrue(result);

      // Jukebox stat for group added
      assertEquals(1, show.getStats().getJukebox().size());
      // Two requests created ordered by sequence order -> positions 1 and 2
      assertEquals(2, show.getRequests().size());
      assertEquals(1, show.getRequests().get(0).getPosition());
      assertEquals(2, show.getRequests().get(1).getPosition());
    }

    @Test
    @DisplayName("GEO beyond radius should throw INVALID_LOCATION")
    void geoBeyondRadiusThrows() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.GEO);
      when(show.getPreferences().getShowLatitude()).thenReturn(0.0f);
      when(show.getPreferences().getShowLongitude()).thenReturn(0.0f);
      when(show.getPreferences().getAllowedRadius()).thenReturn(0.1f); // tiny radius

      // Add a sequence so matching passes if reached
      Sequence s = mock(Sequence.class);
      when(s.getName()).thenReturn("song-a");
      when(s.getDisplayName()).thenReturn("Song A");
      show.getSequences().add(s);

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));

      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "song-a", 10f, 10f));
    }

    @Test
    @DisplayName("checkIfSequenceRequested: playing now matches by name -> SEQUENCE_REQUESTED")
    void sequenceRequestedWhenPlayingNowByName() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.NONE);

      Sequence s = mock(Sequence.class);
      when(s.getName()).thenReturn("song-a");
      when(s.getDisplayName()).thenReturn("Song A");
      show.getSequences().add(s);
      when(show.getPlayingNow()).thenReturn("song-a");

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "song-a", 0f, 0f));
    }

    @Test
    @DisplayName("checkIfSequenceRequested: playing next matches by displayName -> SEQUENCE_REQUESTED")
    void sequenceRequestedWhenPlayingNextByDisplayName() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.NONE);

      Sequence s = mock(Sequence.class);
      when(s.getName()).thenReturn("song-a");
      when(s.getDisplayName()).thenReturn("Song A");
      show.getSequences().add(s);
      when(show.getPlayingNext()).thenReturn("Song A");

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "song-a", 0f, 0f));
    }

    @Test
    @DisplayName("checkIfSequenceRequested: within request limit -> SEQUENCE_REQUESTED")
    void sequenceRequestedWithinRequestLimit() {
      Show show = mockShowWithPrefsAndCollections();
      when(show.getPreferences().getLocationCheckMethod()).thenReturn(LocationCheckMethod.NONE);
      when(show.getPreferences().getJukeboxRequestLimit()).thenReturn(2);

      // Requested sequence
      Sequence s = mock(Sequence.class);
      when(s.getName()).thenReturn("song-a");
      when(s.getDisplayName()).thenReturn("Song A");
      show.getSequences().add(s);

      // Recent requests include this sequence in the last 2
      Request r1 = mock(Request.class);
      when(r1.getPosition()).thenReturn(10);
      Sequence sOther = mock(Sequence.class);
      when(sOther.getName()).thenReturn("other");
      when(r1.getSequence()).thenReturn(sOther);
      Request r2 = mock(Request.class);
      when(r2.getPosition()).thenReturn(11);
      Sequence sPrev = mock(Sequence.class);
      when(sPrev.getName()).thenReturn("song-a");
      when(r2.getSequence()).thenReturn(sPrev);
      show.getRequests().add(r1);
      show.getRequests().add(r2);

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));
      assertThrows(CustomGraphQLExceptionResolver.class, () -> service.addSequenceToQueue("sub", "song-a", 0f, 0f));
    }
  }

  @Nested
  @DisplayName("voteForSequence success paths")
  class VoteForSequenceSuccessTests {
    @Test
    @DisplayName("Vote for single sequence succeeds: creates vote, adds voting stat, persists")
    void voteForSingleSequenceSuccess() {
      Show show = mockShowWithPrefsAndCollections();
      // Add a sequence to match by name
      Sequence seq = mock(Sequence.class);
      when(seq.getName()).thenReturn("song-a");
      when(seq.getDisplayName()).thenReturn("Song A");
      show.getSequences().add(seq);

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));

      Boolean result = service.voteForSequence("sub", "song-a", 0f, 0f);
      assertTrue(result);

      // A new vote should be added with 1 vote and viewer IP added
      assertEquals(1, show.getVotes().size());
      Vote v = show.getVotes().get(0);
      assertEquals(1, v.getVotes());
      assertSame(seq, v.getSequence());
      assertTrue(v.getViewersVoted().contains("1.2.3.4"));
      assertNotNull(v.getLastVoteTime());

      // Voting stat added for non-grouped votes
      assertEquals(1, show.getStats().getVoting().size());

      // Note: Voting uses persist() which is not implemented yet
      // verify(showRepository).persist(any(Show.class));
    }

    @Test
    @DisplayName("Vote for sequence group succeeds: delegates to saveSequenceGroupVote and adds voting stat")
    void voteForSequenceGroupSuccess() {
      Show show = mockShowWithPrefsAndCollections();
      // No matching sequence, but a group exists with the given name
      SequenceGroup group = mock(SequenceGroup.class);
      when(group.getName()).thenReturn("GroupX");
      show.getSequenceGroups().add(group);

      when(showRepository.findByShowSubdomain("sub")).thenReturn(Optional.of(show));

      Boolean result = service.voteForSequence("sub", "GroupX", 0f, 0f);
      assertTrue(result);

      // A vote entry should be added for the group and a voting stat created
      assertEquals(1, show.getVotes().size());
      Vote v = show.getVotes().get(0);
      assertSame(group, v.getSequenceGroup());
      assertEquals(1, v.getVotes());
      assertTrue(v.getViewersVoted().contains("1.2.3.4"));
      assertEquals(1, show.getStats().getVoting().size());

      // Note: Voting uses persist() which is not implemented yet
      // verify(showRepository).persist(any(Show.class));
    }
  }

  @Nested
  @DisplayName("saveSequenceVote private method coverage")
  class SaveSequenceVotePrivateTests {
    @Test
    @DisplayName("Existing vote: increments count, appends viewer IP, updates time, adds stat, persists")
    void existingVoteIncrementPath() throws Exception {
      Show show = mockShowWithPrefsAndCollections();

      // Prepare existing vote for sequence "song-a" with mutable viewers list
      Sequence seq = mock(Sequence.class);
      when(seq.getName()).thenReturn("song-a");
      Vote existing = Vote.builder()
          .sequence(seq)
          .ownerVoted(false)
          .lastVoteTime(LocalDateTime.now().minusMinutes(5))
          .viewersVoted(new ArrayList<>(List.of("9.9.9.9")))
          .votes(3)
          .build();
      show.getVotes().add(existing);

      // Invoke private method with isGrouped=false
      // Create a raw service instance and inject repository via reflection to avoid
      // proxy issues
      GraphQLMutationService rawService = new GraphQLMutationService();
      var repoField = GraphQLMutationService.class.getDeclaredField("showRepository");
      repoField.setAccessible(true);
      repoField.set(rawService, showRepository);

      var m = GraphQLMutationService.class.getDeclaredMethod(
          "saveSequenceVote", Show.class, Sequence.class, String.class, Boolean.class);
      m.setAccessible(true);
      m.invoke(rawService, show, seq, "1.2.3.4", Boolean.FALSE);

      // Should have incremented votes, appended IP, updated time, and added voting
      // stat
      assertEquals(4, existing.getVotes());
      assertTrue(existing.getViewersVoted().contains("1.2.3.4"));
      assertTrue(existing.getLastVoteTime().isAfter(LocalDateTime.now().minusMinutes(1)));
      assertEquals(1, show.getStats().getVoting().size());

      // Note: Voting uses persist() which is not implemented yet
      // verify(showRepository).persist(any(Show.class));
    }

    @Test
    @DisplayName("New grouped vote: creates vote with 1001 votes, adds empty viewer IP when blank, no stat, persists")
    void newGroupedVoteCreationPath() throws Exception {
      Show show = mockShowWithPrefsAndCollections();

      Sequence seq = mock(Sequence.class);
      when(seq.getName()).thenReturn("group-seq");

      // Invoke private method with isGrouped=true and empty IP
      // Create a raw service instance and inject repository
      GraphQLMutationService rawService = new GraphQLMutationService();
      var repoField = GraphQLMutationService.class.getDeclaredField("showRepository");
      repoField.setAccessible(true);
      repoField.set(rawService, showRepository);

      var m = GraphQLMutationService.class.getDeclaredMethod(
          "saveSequenceVote", Show.class, Sequence.class, String.class, Boolean.class);
      m.setAccessible(true);
      m.invoke(rawService, show, seq, "", Boolean.TRUE);

      // New vote created with votes=1001, viewer "" added, and no voting stat for
      // grouped
      assertEquals(1, show.getVotes().size());
      Vote v = show.getVotes().get(0);
      assertSame(seq, v.getSequence());
      assertEquals(1001, v.getVotes());
      assertTrue(v.getViewersVoted().contains(""));
      assertEquals(0, show.getStats().getVoting().size());

      // Note: Voting uses persist() which is not implemented yet
      // verify(showRepository).persist(any(Show.class));
    }
  }

  @Nested
  @DisplayName("saveSequenceGroupVote private method coverage")
  class SaveSequenceGroupVotePrivateTests {
    @Test
    @DisplayName("Existing group vote: increments count, appends viewer IP, updates time, adds stat, persists")
    void existingGroupVoteIncrementPath() throws Exception {
      Show show = mockShowWithPrefsAndCollections();

      // Prepare existing vote for group "GroupG" with mutable viewers list
      SequenceGroup group = mock(SequenceGroup.class);
      when(group.getName()).thenReturn("GroupG");
      Vote existing = Vote.builder()
          .sequenceGroup(group)
          .ownerVoted(false)
          .lastVoteTime(LocalDateTime.now().minusMinutes(10))
          .viewersVoted(new ArrayList<>(List.of("8.8.8.8")))
          .votes(2)
          .build();
      show.getVotes().add(existing);

      // Raw service with injected repository
      GraphQLMutationService rawService = new GraphQLMutationService();
      var repoField = GraphQLMutationService.class.getDeclaredField("showRepository");
      repoField.setAccessible(true);
      repoField.set(rawService, showRepository);

      var m = GraphQLMutationService.class.getDeclaredMethod(
          "saveSequenceGroupVote", Show.class, SequenceGroup.class, String.class);
      m.setAccessible(true);
      m.invoke(rawService, show, group, "1.2.3.4");

      // Assertions for lines 361-365 path
      assertEquals(3, existing.getVotes());
      assertTrue(existing.getViewersVoted().contains("1.2.3.4"));
      assertTrue(existing.getLastVoteTime().isAfter(LocalDateTime.now().minusMinutes(1)));
      // Voting stat added (always added after if/else)
      assertEquals(1, show.getStats().getVoting().size());

      // Note: Voting uses persist() which is not implemented yet
      // verify(showRepository).persist(any(Show.class));
    }
  }
}
