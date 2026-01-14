package com.remotefalcon.service;

import com.remotefalcon.library.enums.ViewerControlMode;
import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.util.ClientUtil;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class ViewerStatusService {
  static final String STATUS_ALREADY_REQUESTED = "ALREADY_REQUESTED";
  static final String STATUS_ALREADY_VOTED = "ALREADY_VOTED";

  @Inject
  RoutingContext context;

  public String buildViewerStatus(Show show) {
    if (show == null || show.getPreferences() == null) {
      return null;
    }

    String clientIp = ClientUtil.getClientIP(context);
    ViewerControlMode mode = show.getPreferences().getViewerControlMode();
    if (mode == ViewerControlMode.VOTING) {
      if (StringUtils.isNotBlank(clientIp) && hasViewerVoted(show, clientIp)) {
        return STATUS_ALREADY_VOTED;
      }
      return null;
    }
    if (StringUtils.isNotBlank(clientIp) && hasViewerRequested(show, clientIp)) {
      return STATUS_ALREADY_REQUESTED;
    }
    return null;
  }

  private boolean hasViewerRequested(Show show, String ipAddress) {
    if (BooleanUtils.isTrue(show.getPreferences().getCheckIfRequested())) {
      return show.getRequests().stream()
          .anyMatch(request -> StringUtils.equalsIgnoreCase(ipAddress, request.getViewerRequested()));
    }
    return false;
  }

  private boolean hasViewerVoted(Show show, String ipAddress) {
    if (BooleanUtils.isTrue(show.getPreferences().getCheckIfVoted())) {
      return show.getVotes().stream().anyMatch(vote -> vote.getViewersVoted().contains(ipAddress));
    }
    return false;
  }
}
