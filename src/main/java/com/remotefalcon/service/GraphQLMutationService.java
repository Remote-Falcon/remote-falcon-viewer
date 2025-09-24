package com.remotefalcon.service;

import com.remotefalcon.exception.CustomGraphQLExceptionResolver;
import com.remotefalcon.library.enums.LocationCheckMethod;
import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.library.enums.ViewerControlMode;
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
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent()) {
            Show existingShow = show.get();
            String clientIp = ClientUtil.getClientIP(context);
            if(StringUtils.equalsIgnoreCase(existingShow.getLastLoginIp(), clientIp)) {
                return true;
            }
            Stat.Page pageStat = Stat.Page.builder()
                    .ip(clientIp)
                    .dateTime(date)
                    .build();
            this.showRepository.appendPageStat(showSubdomain, pageStat);
            if(existingShow.getStats() != null && existingShow.getStats().getPage() != null) {
                existingShow.getStats().getPage().add(pageStat);
            }
            return true;
        }
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
    }



    public Boolean updateActiveViewers(String showSubdomain) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent()) {
            Show existingShow = show.get();
            String clientIp = ClientUtil.getClientIP(context);
            if(StringUtils.equalsIgnoreCase(existingShow.getLastLoginIp(), clientIp)) {
                return true;
            }
            ActiveViewer activeViewer = ActiveViewer.builder()
                    .ipAddress(clientIp)
                    .visitDateTime(LocalDateTime.now())
                    .build();
            List<ActiveViewer> activeViewers = existingShow.getActiveViewers();
            if(activeViewers == null) {
                activeViewers = new ArrayList<>();
                existingShow.setActiveViewers(activeViewers);
            }
            activeViewers.removeIf(viewer -> viewer != null && StringUtils.equalsIgnoreCase(viewer.getIpAddress(), clientIp));
            activeViewers.add(activeViewer);
            this.showRepository.refreshActiveViewer(showSubdomain, activeViewer, existingShow.getLastLoginIp());
            return true;
        }
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
    }


    public Boolean updatePlayingNow(String showSubdomain, String playingNow) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent()) {
            Show existingShow = show.get();
            this.showRepository.updatePlayingNow(showSubdomain, playingNow);
            existingShow.setPlayingNow(playingNow);
            return true;
        }
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
    }


    public Boolean updatePlayingNext(String showSubdomain, String playingNext) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent()) {
            Show existingShow = show.get();
            this.showRepository.updatePlayingNext(showSubdomain, playingNext);
            existingShow.setPlayingNext(playingNext);
            return true;
        }
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
    }


    public Boolean addSequenceToQueue(String showSubdomain, String name, Float latitude, Float longitude) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent()) {
            Show existingShow = show.get();
            String clientIp = ClientUtil.getClientIP(context);
            if(StringUtils.isEmpty(clientIp)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
            }
            if(this.isIpBlocked(clientIp, show.get())) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.NAUGHTY.name());
            }
            if(this.hasViewerRequested(show.get(), clientIp)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.ALREADY_REQUESTED.name());
            }
            if(this.isQueueFull(existingShow)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.QUEUE_FULL.name());
            }
            if(!this.isViewerPresent(existingShow, latitude, longitude)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.INVALID_LOCATION.name());
            }
            Optional<Sequence> requestedSequence = show.get().getSequences().stream()
                    .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
                    .findFirst();
            if(requestedSequence.isPresent()) {
                this.checkIfSequenceRequested(show.get(), requestedSequence.get());
                Stat.Jukebox jukeboxStat = Stat.Jukebox.builder()
                        .dateTime(LocalDateTime.now())
                        .name(requestedSequence.get().getName())
                        .build();
                this.showRepository.appendJukeboxStat(showSubdomain, jukeboxStat);
                show.get().getStats().getJukebox().add(jukeboxStat);
                this.saveSequenceRequest(showSubdomain, show.get(), requestedSequence.get(), clientIp);
                if(show.get().getPreferences().getPsaEnabled() && !show.get().getPreferences().getManagePsa() && CollectionUtils.isNotEmpty(show.get().getPsaSequences())) {
                    this.handlePsaForJukebox(showSubdomain, show.get());
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
                    Stat.Jukebox jukeboxStat = Stat.Jukebox.builder()
                            .dateTime(LocalDateTime.now())
                            .name(requestedSequenceGroup.get().getName())
                            .build();
                    this.showRepository.appendJukeboxStat(showSubdomain, jukeboxStat);
                    show.get().getStats().getJukebox().add(jukeboxStat);
                    sequencesInGroup.forEach(sequence -> {
                        this.checkIfSequenceRequested(show.get(), sequence);
                        this.saveSequenceRequest(showSubdomain, show.get(), sequence, clientIp);
                    });
                    if(show.get().getPreferences().getPsaEnabled() && !show.get().getPreferences().getManagePsa() && CollectionUtils.isNotEmpty(show.get().getPsaSequences())) {
                        this.handlePsaForJukebox(showSubdomain, show.get());
                    }
                    return true;
                }
            }
            throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
        }
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
    }

    public Boolean voteForSequence(String showSubdomain, String name, Float latitude, Float longitude) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent()) {
            Show existingShow = show.get();
            String clientIp = ClientUtil.getClientIP(context);
            if(StringUtils.isEmpty(clientIp)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
            }
            if(this.isIpBlocked(clientIp, existingShow)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.NAUGHTY.name());
            }
            if(this.hasViewerVoted(existingShow, clientIp)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.ALREADY_VOTED.name());
            }
            if(!this.isViewerPresent(existingShow, latitude, longitude)) {
                throw new CustomGraphQLExceptionResolver(StatusResponse.INVALID_LOCATION.name());
            }
            Optional<Sequence> requestedSequence = existingShow.getSequences().stream()
                    .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
                    .findFirst();
            if(requestedSequence.isPresent()) {
                this.saveSequenceVote(showSubdomain, existingShow, requestedSequence.get(), clientIp, false);
                return true;
            }else { //It's a sequence group
                Optional<SequenceGroup> votedSequenceGroup = existingShow.getSequenceGroups().stream()
                        .filter(seq -> StringUtils.equalsIgnoreCase(seq.getName(), name))
                        .findFirst();
                if(votedSequenceGroup.isPresent()) {
                    this.saveSequenceGroupVote(showSubdomain, existingShow, votedSequenceGroup.get(), clientIp);
                    return true;
                }
            }
        }
        throw new CustomGraphQLExceptionResolver(StatusResponse.UNEXPECTED_ERROR.name());
    }

    private boolean isIpBlocked(String ipAddress, Show show) {
        if(CollectionUtils.isNotEmpty(show.getPreferences().getBlockedViewerIps())) {
            return show.getPreferences().getBlockedViewerIps().contains(ipAddress);
        }
        return false;
    }

    private Boolean hasViewerRequested(Show show, String ipAddress) {
        if(BooleanUtils.isTrue(show.getPreferences().getCheckIfRequested())) {
            return show.getRequests().stream().anyMatch(request -> StringUtils.equalsIgnoreCase(ipAddress, request.getViewerRequested()));
        }
        return false;
    }

    private Boolean hasViewerVoted(Show show, String ipAddress) {
        if(BooleanUtils.isTrue(show.getPreferences().getCheckIfVoted())) {
            return show.getVotes().stream().anyMatch(vote -> vote.getViewersVoted().contains(ipAddress));
        }
        return false;
    }

    private Boolean isQueueFull(Show show) {
        if(CollectionUtils.isNotEmpty(show.getRequests())) {
            return show.getPreferences().getJukeboxDepth() != 0 && show.getRequests().size() >= show.getPreferences().getJukeboxDepth();
        }
        return false;
    }

    private Boolean isViewerPresent(Show show, Float latitude, Float longitude) {
        if(latitude == null || longitude == null) {
            return false;
        }
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

    private void checkIfSequenceRequested(Show show, Sequence requestedSequence) {
        if(this.isRequestedSequencePlayingNow(show, requestedSequence)) {
            throw new CustomGraphQLExceptionResolver(StatusResponse.SEQUENCE_REQUESTED.name());
        }
        if(this.isRequestedSequencePlayingNext(show, requestedSequence)) {
            throw new CustomGraphQLExceptionResolver(StatusResponse.SEQUENCE_REQUESTED.name());
        }
        if(this.isRequestedSequenceWithinRequestLimit(show, requestedSequence)) {
            throw new CustomGraphQLExceptionResolver(StatusResponse.SEQUENCE_REQUESTED.name());
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

    private void saveSequenceRequest(String showSubdomain, Show show, Sequence requestedSequence, String ipAddress) {
        long nextPosition = this.showRepository.nextRequestPosition(showSubdomain);
        Request request = Request.builder()
                .sequence(requestedSequence)
                .ownerRequested(false)
                .viewerRequested(StringUtils.isEmpty(ipAddress) ? "" : ipAddress)
                .position(Math.toIntExact(nextPosition))
                .build();
        this.showRepository.appendRequest(showSubdomain, request);
        if(CollectionUtils.isEmpty(show.getRequests())) {
            show.setRequests(new ArrayList<>());
        }
        show.getRequests().add(request);
    }


    private void handlePsaForJukebox(String showSubdomain, Show show) {
        Integer requestsMadeToday = show.getStats().getJukebox().stream()
                .filter(stat -> stat.getDateTime().isAfter(LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)))
                .toList()
                .size();
        if(requestsMadeToday % show.getPreferences().getPsaFrequency() == 0) {
            Optional<PsaSequence> nextPsaSequence = show.getPsaSequences().stream()
                    .min(Comparator.comparing(PsaSequence::getLastPlayed)
                            .thenComparing(PsaSequence::getOrder));
            if(nextPsaSequence.isPresent()) {
                PsaSequence psaSequence = nextPsaSequence.get();
                LocalDateTime now = LocalDateTime.now();
                Optional<Sequence> sequenceToAdd = show.getSequences().stream()
                        .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), psaSequence.getName()))
                        .findFirst();
                psaSequence.setLastPlayed(now);
                this.showRepository.updatePsaLastPlayed(showSubdomain, psaSequence.getName(), now);
                sequenceToAdd.ifPresent(sequence -> this.saveSequenceRequest(showSubdomain, show, sequence, "PSA"));
            }
        }
    }


    private void saveSequenceVote(String showSubdomain, Show show, Sequence votedSequence, String ipAddress, Boolean isGrouped) {
        LocalDateTime now = LocalDateTime.now();
        String voter = StringUtils.isEmpty(ipAddress) ? "" : ipAddress;
        int increment = Boolean.TRUE.equals(isGrouped) ? 1001 : 1;
        boolean updated = this.showRepository.incrementSequenceVote(showSubdomain, votedSequence.getName(), increment, voter, now);
        if(updated) {
            show.getVotes().stream()
                    .filter(vote -> vote.getSequence() != null)
                    .filter(vote -> StringUtils.equalsIgnoreCase(vote.getSequence().getName(), votedSequence.getName()))
                    .findFirst()
                    .ifPresent(vote -> {
                        vote.setVotes(vote.getVotes() + increment);
                        vote.getViewersVoted().add(voter);
                        vote.setLastVoteTime(now);
                        vote.setOwnerVoted(false);
                    });
        }else {
            List<String> voters = new ArrayList<>();
            voters.add(voter);
            Vote vote = Vote.builder()
                    .sequence(votedSequence)
                    .ownerVoted(false)
                    .lastVoteTime(now)
                    .viewersVoted(voters)
                    .votes(increment)
                    .build();
            this.showRepository.appendVote(showSubdomain, vote);
            if(CollectionUtils.isEmpty(show.getVotes())) {
                show.setVotes(new ArrayList<>());
            }
            show.getVotes().add(vote);
        }
        if(!Boolean.TRUE.equals(isGrouped)) {
            Stat.Voting votingStat = Stat.Voting.builder()
                    .dateTime(now)
                    .name(votedSequence.getName())
                    .build();
            this.showRepository.appendVotingStat(showSubdomain, votingStat);
            if(CollectionUtils.isEmpty(show.getStats().getVoting())) {
                show.getStats().setVoting(new ArrayList<>());
            }
            show.getStats().getVoting().add(votingStat);
        }
    }



    private void saveSequenceGroupVote(String showSubdomain, Show show, SequenceGroup votedSequenceGroup, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        boolean updated = this.showRepository.incrementSequenceGroupVote(showSubdomain, votedSequenceGroup.getName(), ipAddress, now);
        if(updated) {
            show.getVotes().stream()
                    .filter(vote -> vote.getSequenceGroup() != null)
                    .filter(vote -> StringUtils.equalsIgnoreCase(vote.getSequenceGroup().getName(), votedSequenceGroup.getName()))
                    .findFirst()
                    .ifPresent(vote -> {
                        vote.setVotes(vote.getVotes() + 1);
                        vote.getViewersVoted().add(ipAddress);
                        vote.setLastVoteTime(now);
                        vote.setOwnerVoted(false);
                    });
        }else {
            List<String> voters = new ArrayList<>();
            voters.add(ipAddress);
            Vote vote = Vote.builder()
                    .sequenceGroup(votedSequenceGroup)
                    .ownerVoted(false)
                    .lastVoteTime(now)
                    .viewersVoted(voters)
                    .votes(1)
                    .build();
            this.showRepository.appendVote(showSubdomain, vote);
            if(CollectionUtils.isEmpty(show.getVotes())) {
                show.setVotes(new ArrayList<>());
            }
            show.getVotes().add(vote);
        }
        Stat.Voting votingStat = Stat.Voting.builder()
                .dateTime(now)
                .name(votedSequenceGroup.getName())
                .build();
        this.showRepository.appendVotingStat(showSubdomain, votingStat);
        if(CollectionUtils.isEmpty(show.getStats().getVoting())) {
            show.getStats().setVoting(new ArrayList<>());
        }
        show.getStats().getVoting().add(votingStat);
    }

}





