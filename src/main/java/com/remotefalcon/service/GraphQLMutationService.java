package com.remotefalcon.service;

import com.remotefalcon.exception.CustomGraphQLExceptionResolver;
import com.remotefalcon.library.enums.LocationCheckMethod;
import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.library.models.*;
import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.repository.ShowRepository;
import com.remotefalcon.util.ClientUtil;
import com.remotefalcon.util.LocationUtil;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@JBossLog
@ApplicationScoped
public class GraphQLMutationService {
  @Inject
  ShowRepository showRepository;

  @Inject
  RoutingContext context;

  public Boolean insertViewerPageStats(String showSubdomain, LocalDateTime date) {
    String clientIp = ClientUtil.getClientIP(context);
    if (StringUtils.isEmpty(clientIp)) {
      return true; // Skip if no IP available
    }

    // Only append if IP is different from lastLoginIp (owner)
    // Use atomic operation to avoid reading entire document
    Stat.Page pageStat = Stat.Page.builder()
        .ip(clientIp)
        .dateTime(date)
        .build();

    long modifiedCount = this.showRepository.appendPageStatIfNotOwner(showSubdomain, clientIp, pageStat);
    return modifiedCount > 0; // Returns true if stat was added
  }

  public Boolean updateActiveViewers(String showSubdomain) {
    Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
    if (show.isPresent()) {
      Show existingShow = show.get();
      String clientIp = ClientUtil.getClientIP(context);
      if (!StringUtils.equalsIgnoreCase(existingShow.getLastLoginIp(), clientIp)) {
        this.showRepository.updateActiveViewer(showSubdomain, clientIp, LocalDateTime.now());
      }
      return true;
    }
    throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
  }

  public Boolean updatePlayingNow(String showSubdomain, String playingNow) {
    Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
    if (show.isPresent()) {
      this.showRepository.updatePlayingNow(showSubdomain, playingNow);
      return true;
    }
    throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
  }

  public Boolean updatePlayingNext(String showSubdomain, String playingNext) {
    Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
    if (show.isPresent()) {
      this.showRepository.updatePlayingNext(showSubdomain, playingNext);
      return true;
    }
    throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
  }

  public Boolean addSequenceToQueue(String showSubdomain, String name, Float latitude, Float longitude) {
    // Use optimized query that excludes large stats but keeps fields needed for validation
    Optional<Show> show = this.showRepository.findByShowSubdomainForMutations(showSubdomain);
    if (show.isPresent()) {
      Show existingShow = show.get();
      String clientIp = ClientUtil.getClientIP(context);
      if (StringUtils.isEmpty(clientIp)) {
        log.errorf("Client IP not found or empty in addSequenceToQueue: showSubdomain=%s, name=%s", showSubdomain, name);
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
      }
      if (this.isIpBlocked(clientIp, show.get())) {
        throw new CustomGraphQLExceptionResolver(StatusResponse.NAUGHTY.name());
      }
      if (this.hasViewerRequested(show.get(), clientIp)) {
        throw new CustomGraphQLExceptionResolver(StatusResponse.ALREADY_REQUESTED.name());
      }
      if (this.isQueueFull(existingShow)) {
        throw new CustomGraphQLExceptionResolver(StatusResponse.QUEUE_FULL.name());
      }
      if (!this.isViewerPresent(existingShow, latitude, longitude)) {
        throw new CustomGraphQLExceptionResolver(StatusResponse.INVALID_LOCATION.name());
      }
      Optional<Sequence> requestedSequence = show.get().getSequences().stream()
          .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
          .findFirst();
      if (requestedSequence.isPresent()) {
        this.checkIfSequenceRequested(show.get(), requestedSequence.get());

        // Build request and stat
        long nextPosition = this.showRepository.nextRequestPosition(existingShow);
        Request request = Request.builder()
            .sequence(requestedSequence.get())
            .ownerRequested(false)
            .viewerRequested(StringUtils.isEmpty(clientIp) ? "" : clientIp)
            .position(Math.toIntExact(nextPosition))
            .build();
        Stat.Jukebox jukeboxStat = Stat.Jukebox.builder()
            .dateTime(LocalDateTime.now())
            .name(requestedSequence.get().getName())
            .build();

        // Batched write: single DB call for both request and stat
        this.showRepository.appendRequestAndJukeboxStat(showSubdomain, request, jukeboxStat);

        // Handle PSA if needed (calculate inline without re-fetching)
        if (show.get().getPreferences().getPsaEnabled() && !show.get().getPreferences().getManagePsa()
            && CollectionUtils.isNotEmpty(show.get().getPsaSequences())) {
          // Calculate total requests today (existing + 1 we just added)
          int requestsMadeToday = (int) show.get().getStats().getJukebox().stream()
              .filter(stat -> stat.getDateTime().isAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)))
              .count() + 1; // +1 for the request we just added

          this.handlePsaForJukeboxInline(showSubdomain, show.get(), requestsMadeToday);
        }
        return true;
      } else { // It's a sequence group
        Optional<SequenceGroup> requestedSequenceGroup = show.get().getSequenceGroups().stream()
            .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
            .findFirst();
        if (requestedSequenceGroup.isPresent()) {
          List<Sequence> sequencesInGroup = show.get().getSequences().stream()
              .filter(
                  sequence -> StringUtils.equalsIgnoreCase(requestedSequenceGroup.get().getName(), sequence.getGroup()))
              .sorted(Comparator.comparing(Sequence::getOrder))
              .toList();

          // Check all sequences first
          for (Sequence sequence : sequencesInGroup) {
            this.checkIfSequenceRequested(show.get(), sequence);
          }

          // Allocate all positions at once
          long startPosition = this.showRepository.allocatePositionBlock(existingShow, sequencesInGroup.size());

          // Build all requests using allocated positions
          List<Request> requests = new ArrayList<>();
          for (int i = 0; i < sequencesInGroup.size(); i++) {
            Request request = Request.builder()
                .sequence(sequencesInGroup.get(i))
                .ownerRequested(false)
                .viewerRequested(StringUtils.isEmpty(clientIp) ? "" : clientIp)
                .position(Math.toIntExact(startPosition + i))
                .build();
            requests.add(request);
          }
          Stat.Jukebox jukeboxStat = Stat.Jukebox.builder()
              .dateTime(LocalDateTime.now())
              .name(requestedSequenceGroup.get().getName())
              .build();

          // Batched write: single DB call for all requests and stat
          this.showRepository.appendMultipleRequestsAndJukeboxStat(showSubdomain, requests, jukeboxStat);

          // Handle PSA if needed (calculate inline without re-fetching)
          if (show.get().getPreferences().getPsaEnabled() && !show.get().getPreferences().getManagePsa()
              && CollectionUtils.isNotEmpty(show.get().getPsaSequences())) {
            // Calculate total requests today (existing + 1 we just added for the group)
            int requestsMadeToday = (int) show.get().getStats().getJukebox().stream()
                .filter(stat -> stat.getDateTime().isAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)))
                .count() + 1; // +1 for the group request we just added

            this.handlePsaForJukeboxInline(showSubdomain, show.get(), requestsMadeToday);
          }
          return true;
        }
      }
      log.errorf("Sequence or sequence group not found: showSubdomain=%s, name=%s", showSubdomain, name);
      throw new CustomGraphQLExceptionResolver("SEQUENCE_NOT_FOUND");
    }
    log.errorf("Show not found: showSubdomain=%s", showSubdomain);
    throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
  }

  public Boolean voteForSequence(String showSubdomain, String name, Float latitude, Float longitude) {
    // Use optimized query that excludes large stats
    Optional<Show> show = this.showRepository.findByShowSubdomainForMutations(showSubdomain);
    if (show.isPresent()) {
      Show existingShow = show.get();
      String clientIp = ClientUtil.getClientIP(context);
      if (StringUtils.isEmpty(clientIp)) {
        log.errorf("Client IP not found or empty in voteForSequence: showSubdomain=%s, name=%s", showSubdomain, name);
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
      }
      if (this.isIpBlocked(clientIp, existingShow)) {
        throw new CustomGraphQLExceptionResolver(StatusResponse.NAUGHTY.name());
      }
      if (this.hasViewerVoted(existingShow, clientIp)) {
        throw new CustomGraphQLExceptionResolver(StatusResponse.ALREADY_VOTED.name());
      }
      if (!this.isViewerPresent(existingShow, latitude, longitude)) {
        throw new CustomGraphQLExceptionResolver(StatusResponse.INVALID_LOCATION.name());
      }
      Optional<Sequence> requestedSequence = existingShow.getSequences().stream()
          .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
          .findFirst();
      if (requestedSequence.isPresent()) {
        this.saveSequenceVote(existingShow, requestedSequence.get(), clientIp, false);
        return true;
      } else { // It's a sequence group
        Optional<SequenceGroup> votedSequenceGroup = existingShow.getSequenceGroups().stream()
            .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
            .findFirst();
        if (votedSequenceGroup.isPresent()) {
          this.saveSequenceGroupVote(existingShow, votedSequenceGroup.get(), clientIp);
          return true;
        }
      }
    }
    throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
  }

  private boolean isIpBlocked(String ipAddress, Show show) {
    if (CollectionUtils.isNotEmpty(show.getPreferences().getBlockedViewerIps())) {
      return show.getPreferences().getBlockedViewerIps().contains(ipAddress);
    }
    return false;
  }

  private Boolean hasViewerRequested(Show show, String ipAddress) {
    if (BooleanUtils.isTrue(show.getPreferences().getCheckIfRequested())) {
      return show.getRequests().stream()
          .anyMatch(request -> StringUtils.equalsIgnoreCase(ipAddress, request.getViewerRequested()));
    }
    return false;
  }

  private Boolean hasViewerVoted(Show show, String ipAddress) {
    if (BooleanUtils.isTrue(show.getPreferences().getCheckIfVoted())) {
      return show.getVotes().stream().anyMatch(vote -> vote.getViewersVoted().contains(ipAddress));
    }
    return false;
  }

  private Boolean isQueueFull(Show show) {
    if (CollectionUtils.isNotEmpty(show.getRequests())) {
      return show.getPreferences().getJukeboxDepth() != 0
          && show.getRequests().size() >= show.getPreferences().getJukeboxDepth();
    }
    return false;
  }

  private Boolean isViewerPresent(Show show, Float latitude, Float longitude) {
    if (show.getPreferences().getLocationCheckMethod() == LocationCheckMethod.GEO) {
      if (latitude == null || longitude == null) {
        return false;
      }
      Double distance = LocationUtil.asTheCrowFlies(
          show.getPreferences().getShowLatitude(),
          show.getPreferences().getShowLongitude(),
          latitude,
          longitude);
      return distance <= show.getPreferences().getAllowedRadius();
    }
    return true;
  }

  private void checkIfSequenceRequested(Show show, Sequence requestedSequence) {
    if (this.isRequestedSequencePlayingNow(show, requestedSequence)) {
      throw new CustomGraphQLExceptionResolver(StatusResponse.SEQUENCE_REQUESTED.name());
    }
    if (this.isRequestedSequencePlayingNext(show, requestedSequence)) {
      throw new CustomGraphQLExceptionResolver(StatusResponse.SEQUENCE_REQUESTED.name());
    }
    if (this.isRequestedSequenceWithinRequestLimit(show, requestedSequence)) {
      throw new CustomGraphQLExceptionResolver(StatusResponse.SEQUENCE_REQUESTED.name());
    }
  }

  private Boolean isRequestedSequencePlayingNow(Show show, Sequence requestedSequence) {
    return StringUtils.equalsIgnoreCase(show.getPlayingNow(), requestedSequence.getName())
        || (StringUtils.isNotEmpty(requestedSequence.getDisplayName())
            && StringUtils.equalsIgnoreCase(show.getPlayingNow(), requestedSequence.getDisplayName()));
  }

  private Boolean isRequestedSequencePlayingNext(Show show, Sequence requestedSequence) {
    return StringUtils.equalsIgnoreCase(show.getPlayingNext(), requestedSequence.getName())
        || (StringUtils.isNotEmpty(requestedSequence.getDisplayName())
            && StringUtils.equalsIgnoreCase(show.getPlayingNext(), requestedSequence.getDisplayName()));
  }

  private Boolean isRequestedSequenceWithinRequestLimit(Show show, Sequence requestedSequence) {
    if (show.getPreferences().getJukeboxRequestLimit() != 0) {
      List<String> requestNamesLastToFirst = show.getRequests().stream()
          .sorted(Comparator.comparing(Request::getPosition)
              .reversed())
          .limit(show.getPreferences().getJukeboxRequestLimit())
          .map(request -> request.getSequence().getName())
          .toList();
      return requestNamesLastToFirst.contains(requestedSequence.getName());
    }
    return false;
  }

  private void saveSequenceRequest(String showSubdomain, Show show, Sequence requestedSequence, String ipAddress) {
    long nextPosition = this.showRepository.nextRequestPosition(show);
    Request request = Request.builder()
        .sequence(requestedSequence)
        .ownerRequested(false)
        .viewerRequested(StringUtils.isEmpty(ipAddress) ? "" : ipAddress)
        .position(Math.toIntExact(nextPosition))
        .build();
    this.showRepository.appendRequest(showSubdomain, request);
    if (CollectionUtils.isEmpty(show.getRequests())) {
      show.setRequests(new ArrayList<>());
    }
    show.getRequests().add(request);
  }

  private void handlePsaForJukebox(String showSubdomain, Show show) {
    Integer requestsMadeToday = show.getStats().getJukebox().stream()
        .filter(stat -> stat.getDateTime().isAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)))
        .toList()
        .size();
    if (requestsMadeToday % show.getPreferences().getPsaFrequency() == 0) {
      Optional<PsaSequence> nextPsaSequence = show.getPsaSequences().stream()
          .min(Comparator.comparing(PsaSequence::getLastPlayed)
              .thenComparing(PsaSequence::getOrder));
      if (nextPsaSequence.isPresent()) {
        Optional<Sequence> sequenceToAdd = show.getSequences().stream()
            .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), nextPsaSequence.get().getName()))
            .findFirst();
        show.getPsaSequences().get(show.getPsaSequences().indexOf(nextPsaSequence.get()))
            .setLastPlayed(LocalDateTime.now());
        sequenceToAdd.ifPresent(sequence -> this.saveSequenceRequest(showSubdomain, show, sequence, "PSA"));
      }
    }
  }

  private void handlePsaForJukeboxInline(String showSubdomain, Show show, int requestsMadeToday) {
    // Inline PSA handling that doesn't require re-fetching show
    if (requestsMadeToday % show.getPreferences().getPsaFrequency() == 0) {
      Optional<PsaSequence> nextPsaSequence = show.getPsaSequences().stream()
          .min(Comparator.comparing(PsaSequence::getLastPlayed)
              .thenComparing(PsaSequence::getOrder));
      if (nextPsaSequence.isPresent()) {
        Optional<Sequence> sequenceToAdd = show.getSequences().stream()
            .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), nextPsaSequence.get().getName()))
            .findFirst();
        show.getPsaSequences().get(show.getPsaSequences().indexOf(nextPsaSequence.get()))
            .setLastPlayed(LocalDateTime.now());
        sequenceToAdd.ifPresent(sequence -> this.saveSequenceRequest(showSubdomain, show, sequence, "PSA"));
      }
    }
  }

  private void saveSequenceVote(Show show, Sequence votedSequence, String ipAddress, Boolean isGrouped) {
    Optional<Vote> sequenceVotes = show.getVotes().stream()
        .filter(vote -> vote.getSequence() != null)
        .filter(vote -> StringUtils.equalsIgnoreCase(vote.getSequence().getName(), votedSequence.getName()))
        .findFirst();

    LocalDateTime voteTime = LocalDateTime.now();
    String voterIp = StringUtils.isEmpty(ipAddress) ? "" : ipAddress;

    if (sequenceVotes.isPresent()) {
      // Existing vote: increment count, append voter, update time, and add stat
      Stat.Voting votingStat = isGrouped ? null : Stat.Voting.builder()
          .dateTime(voteTime)
          .name(votedSequence.getName())
          .build();

      if (isGrouped) {
        // For grouped votes, don't add voting stat
        this.showRepository.incrementVoteAndAppendVoter(show.getShowSubdomain(), votedSequence.getName(), voterIp, voteTime, null);
      } else {
        this.showRepository.incrementVoteAndAppendVoter(show.getShowSubdomain(), votedSequence.getName(), voterIp, voteTime, votingStat);
      }
    } else {
      // New vote: add vote entry and stat
      Vote newVote = Vote.builder()
          .sequence(votedSequence)
          .ownerVoted(false)
          .lastVoteTime(voteTime)
          .viewersVoted(List.of(voterIp))
          .votes(isGrouped ? 1001 : 1)
          .build();

      Stat.Voting votingStat = isGrouped ? null : Stat.Voting.builder()
          .dateTime(voteTime)
          .name(votedSequence.getName())
          .build();

      this.showRepository.addNewVoteAndStat(show.getShowSubdomain(), newVote, votingStat);
    }
  }

  private void saveSequenceGroupVote(Show show, SequenceGroup votedSequenceGroup, String ipAddress) {
    Optional<Vote> sequenceVotes = show.getVotes().stream()
        .filter(vote -> vote.getSequenceGroup() != null)
        .filter(vote -> StringUtils.equalsIgnoreCase(vote.getSequenceGroup().getName(), votedSequenceGroup.getName()))
        .findFirst();

    LocalDateTime voteTime = LocalDateTime.now();
    Stat.Voting votingStat = Stat.Voting.builder()
        .dateTime(voteTime)
        .name(votedSequenceGroup.getName())
        .build();

    if (sequenceVotes.isPresent()) {
      // Existing vote: increment count, append voter, update time, and add stat
      this.showRepository.incrementSequenceGroupVoteAndAppendVoter(
          show.getShowSubdomain(),
          votedSequenceGroup.getName(),
          ipAddress,
          voteTime,
          votingStat
      );
    } else {
      // New vote: add vote entry and stat
      Vote newVote = Vote.builder()
          .sequenceGroup(votedSequenceGroup)
          .ownerVoted(false)
          .lastVoteTime(voteTime)
          .viewersVoted(List.of(ipAddress))
          .votes(1)
          .build();

      this.showRepository.addNewVoteAndStat(show.getShowSubdomain(), newVote, votingStat);
    }
  }
}
