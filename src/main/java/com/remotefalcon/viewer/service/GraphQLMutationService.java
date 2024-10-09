package com.remotefalcon.viewer.service;

import com.remotefalcon.library.documents.Show;
import com.remotefalcon.library.enums.LocationCheckMethod;
import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.library.models.*;
import com.remotefalcon.viewer.repository.ShowRepository;
import com.remotefalcon.viewer.util.AuthUtil;
import com.remotefalcon.viewer.util.ClientUtil;
import com.remotefalcon.viewer.util.LocationUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphQLMutationService {
    private final AuthUtil authUtil;
    private final ClientUtil clientUtil;
    private final ShowRepository showRepository;
    private final HttpServletRequest httpServletRequest;

    public Boolean insertViewerPageStats(LocalDateTime date) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent()) {
            String ipAddress = this.clientUtil.getClientIp(httpServletRequest);
            if(!StringUtils.equalsIgnoreCase(show.get().getLastLoginIp(), ipAddress)) {
                show.get().getStats().getPage().add(Stat.Page.builder()
                        .ip(ipAddress)
                        .dateTime(date)
                        .build());
                this.showRepository.save(show.get());
                return true;
            }
            return true;
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    public Boolean updateActiveViewers() {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent()) {
            String ipAddress = this.clientUtil.getClientIp(httpServletRequest);
            List<String> existingIpAddresses = show.get().getActiveViewers().stream().map(ActiveViewer::getIpAddress).toList();
            if(!StringUtils.equalsIgnoreCase(show.get().getLastLoginIp(), ipAddress)) {
                if(existingIpAddresses.contains(ipAddress)) {
                    Optional<ActiveViewer> activeViewer = show.get().getActiveViewers().stream()
                            .filter(viewer -> StringUtils.equalsIgnoreCase(viewer.getIpAddress(), ipAddress))
                            .findFirst();
                    activeViewer.ifPresent(viewer -> show.get().getActiveViewers().remove(viewer));
                }
                show.get().getActiveViewers().add(ActiveViewer.builder()
                        .ipAddress(ipAddress)
                        .visitDateTime(LocalDateTime.now())
                        .build());
                this.showRepository.save(show.get());
            }
            return true;
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    public Boolean updatePlayingNow(String playingNow) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent()) {
            show.get().setPlayingNow(playingNow);
            this.showRepository.save(show.get());
            return true;
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    public Boolean updatePlayingNext(String playingNext) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent()) {
            show.get().setPlayingNext(playingNext);
            this.showRepository.save(show.get());
            return true;
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    public Boolean addSequenceToQueue(String name, Float latitude, Float longitude) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent()) {
            String ipAddress = this.clientUtil.getClientIp(httpServletRequest);
            if(this.isIpBlocked(ipAddress, show.get())) {
                throw new RuntimeException(StatusResponse.NAUGHTY.name());
            }
            if(this.hasViewerRequested(show.get(), ipAddress)) {
                throw new RuntimeException(StatusResponse.ALREADY_REQUESTED.name());
            }
            if(this.isQueueFull(show.get())) {
                throw new RuntimeException(StatusResponse.QUEUE_FULL.name());
            }
            if(!this.isViewerPresent(show.get(), latitude, longitude)) {
                throw new RuntimeException(StatusResponse.INVALID_LOCATION.name());
            }
            Optional<Sequence> requestedSequence = show.get().getSequences().stream()
                    .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
                    .findFirst();
            if(requestedSequence.isPresent()) {
                this.checkIfSequenceRequested(show.get(), requestedSequence.get());
                show.get().getStats().getJukebox().add(Stat.Jukebox.builder()
                        .dateTime(LocalDateTime.now())
                        .name(requestedSequence.get().getName())
                        .build());
                this.saveSequenceRequest(show.get(), requestedSequence.get(), ipAddress);
                if(show.get().getPreferences().getPsaEnabled() && !show.get().getPreferences().getManagePsa() && CollectionUtils.isNotEmpty(show.get().getPsaSequences())) {
                    this.handlePsaForJukebox(show.get());
                }
                return true;
            }else { //It's a sequence group
                Optional<SequenceGroup> requestedSequenceGroup = show.get().getSequenceGroups().stream()
                        .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
                        .findFirst();
                if(requestedSequenceGroup.isPresent()) {
                    List<Sequence> sequencesInGroup = show.get().getSequences().stream()
                            .filter(sequence -> StringUtils.equalsIgnoreCase(requestedSequenceGroup.get().getName(), sequence.getGroup()))
                            .sorted(Comparator.comparing(Sequence::getOrder))
                            .toList();
                    show.get().getStats().getJukebox().add(Stat.Jukebox.builder()
                            .dateTime(LocalDateTime.now())
                            .name(requestedSequenceGroup.get().getName())
                            .build());
                    sequencesInGroup.forEach(sequence -> {
                        this.checkIfSequenceRequested(show.get(), sequence);
                        this.saveSequenceRequest(show.get(), sequence, ipAddress);
                    });
                    if(show.get().getPreferences().getPsaEnabled() && !show.get().getPreferences().getManagePsa() && CollectionUtils.isNotEmpty(show.get().getPsaSequences())) {
                        this.handlePsaForJukebox(show.get());
                    }
                    return true;
                }
            }
            log.error("Failed to request {}: {}", name, show.get().getShowName());
            throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
        }
        log.error("Failed to request {}", name);
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    public Boolean voteForSequence(String name, Float latitude, Float longitude) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent()) {
            String ipAddress = this.clientUtil.getClientIp(httpServletRequest);
            if(this.isIpBlocked(ipAddress, show.get())) {
                throw new RuntimeException(StatusResponse.NAUGHTY.name());
            }
            if(this.hasViewerVoted(show.get(), ipAddress)) {
                throw new RuntimeException(StatusResponse.ALREADY_VOTED.name());
            }
            if(!this.isViewerPresent(show.get(), latitude, longitude)) {
                throw new RuntimeException(StatusResponse.INVALID_LOCATION.name());
            }
            Optional<Sequence> requestedSequence = show.get().getSequences().stream()
                    .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
                    .findFirst();
            if(requestedSequence.isPresent()) {
                this.saveSequenceVote(show.get(), requestedSequence.get(), ipAddress, false);
                return true;
            }else { //It's a sequence group
                Optional<SequenceGroup> votedSequenceGroup = show.get().getSequenceGroups().stream()
                        .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
                        .findFirst();
                if(votedSequenceGroup.isPresent()) {
                    this.saveSequenceGroupVote(show.get(), votedSequenceGroup.get(), ipAddress);
                    return true;
                }
            }
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    private boolean isIpBlocked(String ipAddress, Show show) {
        if(CollectionUtils.isNotEmpty(show.getPreferences().getBlockedViewerIps())) {
            return show.getPreferences().getBlockedViewerIps().contains(ipAddress);
        }
        return false;
    }

    private Boolean isQueueFull(Show show) {
        if(CollectionUtils.isNotEmpty(show.getRequests())) {
            return show.getRequests().size() >= show.getPreferences().getJukeboxDepth();
        }
        return false;
    }

    private Boolean isViewerPresent(Show show, Float latitude, Float longitude) {
        if(show.getPreferences().getLocationCheckMethod() == LocationCheckMethod.GEO) {
            Double distance = LocationUtil.asTheCrowFlies(
                    show.getPreferences().getShowLatitude(),
                    show.getPreferences().getShowLongitude(),
                    latitude,
                    longitude);
            return distance <= show.getPreferences().getAllowedRadius();
        }
        return true;
    }

    private Boolean hasViewerVoted(Show show, String ipAddress) {
        if(BooleanUtils.isTrue(show.getPreferences().getCheckIfVoted())) {
            return show.getVotes().stream().anyMatch(vote -> vote.getViewersVoted().contains(ipAddress));
        }
        return false;
    }

    private Boolean hasViewerRequested(Show show, String ipAddress) {
        if(BooleanUtils.isTrue(show.getPreferences().getCheckIfRequested())) {
            return show.getRequests().stream().anyMatch(request -> StringUtils.equalsIgnoreCase(ipAddress, request.getViewerRequested()));
        }
        return false;
    }

    private void handlePsaForJukebox(Show show) {
        Integer requestsMadeToday = show.getStats().getJukebox().stream()
                .filter(stat -> stat.getDateTime().isAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)))
                .toList()
                .size();
        if(requestsMadeToday % show.getPreferences().getPsaFrequency() == 0) {
            Optional<PsaSequence> nextPsaSequence = show.getPsaSequences().stream()
                    .min(Comparator.comparing(PsaSequence::getLastPlayed)
                            .thenComparing(PsaSequence::getOrder));
            if(nextPsaSequence.isPresent()) {
                Optional<Sequence> sequenceToAdd = show.getSequences().stream()
                        .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), nextPsaSequence.get().getName()))
                        .findFirst();
                show.getPsaSequences().get(show.getPsaSequences().indexOf(nextPsaSequence.get())).setLastPlayed(LocalDateTime.now());
                sequenceToAdd.ifPresent(sequence -> this.saveSequenceRequest(show, sequence, "PSA"));
            }
        }
    }

    private void checkIfSequenceRequested(Show show, Sequence requestedSequence) {
        if(this.isRequestedSequencePlayingNow(show, requestedSequence)) {
            throw new RuntimeException(StatusResponse.SEQUENCE_REQUESTED.name());
        }
        if(this.isRequestedSequencePlayingNext(show, requestedSequence)) {
            throw new RuntimeException(StatusResponse.SEQUENCE_REQUESTED.name());
        }
        if(this.isRequestedSequenceWithinRequestLimit(show, requestedSequence)) {
            throw new RuntimeException(StatusResponse.SEQUENCE_REQUESTED.name());
        }
    }

    private Boolean isRequestedSequencePlayingNow(Show show, Sequence requestedSequence) {
        return StringUtils.equalsIgnoreCase(show.getPlayingNow(), requestedSequence.getName())
                || StringUtils.equalsIgnoreCase(show.getPlayingNow(), requestedSequence.getDisplayName());
    }

    private Boolean isRequestedSequencePlayingNext(Show show, Sequence requestedSequence) {
        return StringUtils.equalsIgnoreCase(show.getPlayingNext(), requestedSequence.getName())
                || StringUtils.equalsIgnoreCase(show.getPlayingNext(), requestedSequence.getDisplayName());
    }

    private Boolean isRequestedSequenceWithinRequestLimit(Show show, Sequence requestedSequence) {
        if(show.getPreferences().getJukeboxRequestLimit() != 0) {
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

    private void saveSequenceRequest(Show show, Sequence requestedSequence, String ipAddress) {
        if(CollectionUtils.isEmpty(show.getRequests())) {
            show.setRequests(new ArrayList<>());
            show.getRequests().add(Request.builder()
                    .sequence(requestedSequence)
                    .ownerRequested(false)
                    .viewerRequested(ipAddress)
                    .position(1)
                    .build());
        }else {
            Optional<Request> latestRequest = show.getRequests().stream()
                    .max(Comparator.comparing(Request::getPosition));
            latestRequest.ifPresent(request -> show.getRequests().add(Request.builder()
                    .sequence(requestedSequence)
                    .ownerRequested(false)
                    .viewerRequested(ipAddress)
                    .position(request.getPosition() + 1)
                    .build()));
        }
        this.showRepository.save(show);
    }

    private void saveSequenceVote(Show show, Sequence votedSequence, String ipAddress, Boolean isGrouped) {
        Optional<Vote> sequenceVotes = show.getVotes().stream()
                .filter(vote -> vote.getSequence() != null)
                .filter(vote -> StringUtils.equalsIgnoreCase(vote.getSequence().getName(), votedSequence.getName()))
                .findFirst();
        if(sequenceVotes.isPresent()) {
            sequenceVotes.get().setVotes(sequenceVotes.get().getVotes() + 1);
            sequenceVotes.get().getViewersVoted().add(ipAddress);
            sequenceVotes.get().setLastVoteTime(LocalDateTime.now());
        }else {
            show.getVotes().add(Vote.builder()
                    .sequence(votedSequence)
                    .ownerVoted(false)
                    .lastVoteTime(LocalDateTime.now())
                    .viewersVoted(List.of(ipAddress))
                    .votes(isGrouped ? 1001 : 1)
                    .build());
        }
        if(!isGrouped) {
            show.getStats().getVoting().add(Stat.Voting.builder()
                    .dateTime(LocalDateTime.now())
                    .name(votedSequence.getName())
                    .build());
        }
        this.showRepository.save(show);
    }

    private void saveSequenceGroupVote(Show show, SequenceGroup votedSequenceGroup, String ipAddress) {
        Optional<Vote> sequenceVotes = show.getVotes().stream()
                .filter(vote -> vote.getSequenceGroup() != null)
                .filter(vote -> StringUtils.equalsIgnoreCase(vote.getSequenceGroup().getName(), votedSequenceGroup.getName()))
                .findFirst();
        if(sequenceVotes.isPresent()) {
            sequenceVotes.get().setVotes(sequenceVotes.get().getVotes() + 1);
            sequenceVotes.get().getViewersVoted().add(ipAddress);
            sequenceVotes.get().setLastVoteTime(LocalDateTime.now());
        }else {
            show.getVotes().add(Vote.builder()
                    .sequenceGroup(votedSequenceGroup)
                    .ownerVoted(false)
                    .lastVoteTime(LocalDateTime.now())
                    .viewersVoted(List.of(ipAddress))
                    .votes(1)
                    .build());
        }
        show.getStats().getVoting().add(Stat.Voting.builder()
                .dateTime(LocalDateTime.now())
                .name(votedSequenceGroup.getName())
                .build());
        this.showRepository.save(show);
    }

    private void updatePlayingNext(Show show) {
        Optional<Request> nextRequest = show.getRequests().stream()
                .min(Comparator.comparing(Request::getPosition));
        nextRequest.ifPresent(request -> show.setPlayingNext(request.getSequence().getName()));
    }
}
