package com.remotefalcon.service;

import com.remotefalcon.library.enums.ViewerControlMode;
import com.remotefalcon.library.models.Preference;
import com.remotefalcon.library.models.Request;
import com.remotefalcon.library.models.Vote;
import com.remotefalcon.library.quarkus.entity.Show;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import io.quarkus.test.InjectMock;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@QuarkusTest
class ViewerStatusServiceTest {

  @Inject
  ViewerStatusService service;

  @InjectMock
  RoutingContext routingContext;

  @InjectMock
  HttpServerRequest httpServerRequest;

  @BeforeEach
  void setUp() {
    reset(routingContext, httpServerRequest);
    when(routingContext.request()).thenReturn(httpServerRequest);
    when(httpServerRequest.getHeader("CF-Connecting-IP")).thenReturn("1.2.3.4");
  }

  @Test
  @DisplayName("Jukebox mode returns null when allowed")
  void jukeboxReturnsNullWhenAllowed() {
    Show show = baseShow();
    show.getPreferences().setViewerControlMode(ViewerControlMode.JUKEBOX);

    String status = service.buildViewerStatus(show);
    assertNull(status);
  }

  @Test
  @DisplayName("Voting mode returns null when allowed")
  void votingReturnsNullWhenAllowed() {
    Show show = baseShow();
    show.getPreferences().setViewerControlMode(ViewerControlMode.VOTING);

    String status = service.buildViewerStatus(show);
    assertNull(status);
  }

  @Test
  @DisplayName("Jukebox returns ALREADY_REQUESTED when already requested")
  void jukeboxReturnsAlreadyRequested() {
    Show show = baseShow();
    show.getPreferences().setViewerControlMode(ViewerControlMode.JUKEBOX);
    show.getPreferences().setCheckIfRequested(true);
    show.getRequests().add(Request.builder().viewerRequested("1.2.3.4").build());

    String status = service.buildViewerStatus(show);

    assertEquals(ViewerStatusService.STATUS_ALREADY_REQUESTED, status);
  }

  @Test
  @DisplayName("Voting returns ALREADY_VOTED when already voted")
  void votingReturnsAlreadyVoted() {
    Show show = baseShow();
    show.getPreferences().setViewerControlMode(ViewerControlMode.VOTING);
    show.getPreferences().setCheckIfVoted(true);
    show.getVotes().add(Vote.builder().viewersVoted(new ArrayList<>(java.util.List.of("1.2.3.4"))).build());

    String status = service.buildViewerStatus(show);

    assertEquals(ViewerStatusService.STATUS_ALREADY_VOTED, status);
  }

  private Show baseShow() {
    Preference preferences = Preference.builder()
        .viewerControlEnabled(true)
        .viewerPageViewOnly(false)
        .jukeboxDepth(0)
        .jukeboxRequestLimit(0)
        .build();

    return Show.builder()
        .preferences(preferences)
        .sequences(new ArrayList<>())
        .sequenceGroups(new ArrayList<>())
        .requests(new ArrayList<>())
        .votes(new ArrayList<>())
        .build();
  }
}
