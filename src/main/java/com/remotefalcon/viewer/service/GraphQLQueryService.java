package com.remotefalcon.viewer.service;

import com.remotefalcon.library.documents.Show;
import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.library.models.*;
import com.remotefalcon.viewer.repository.ShowRepository;
import com.remotefalcon.viewer.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphQLQueryService {
    private final AuthUtil authUtil;
    private final ShowRepository showRepository;

    public Show getShow() {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent()) {
            if(CollectionUtils.isEmpty(show.get().getRequests())) {
                show.get().setPlayingNext(show.get().getPlayingNextFromSchedule());
            }else {
                this.updatePlayingNextRequest(show.get());
            }
            this.updatePlayingNow(show.get());
            this.updatePlayingNext(show.get());
            show.get().setSequences(this.processSequencesForViewer(show.get()));
            return show.get();
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    private void updatePlayingNow(Show show) {
        Optional<Sequence> playingNowSequence = show.getSequences().stream()
                .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), show.getPlayingNow()))
                .findFirst();
        playingNowSequence.ifPresent(sequence -> show.setPlayingNow(sequence.getDisplayName()));
    }

    private void updatePlayingNext(Show show) {
        Optional<Sequence> playingNextSequence = show.getSequences().stream()
                .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), show.getPlayingNext()))
                .findFirst();
        playingNextSequence.ifPresent(sequence -> show.setPlayingNext(sequence.getDisplayName()));
    }

    private void updatePlayingNextRequest(Show show) {
        Optional<Request> nextRequest = show.getRequests().stream()
                .min(Comparator.comparing(Request::getPosition));
        nextRequest.ifPresent(request -> show.setPlayingNext(request.getSequence().getDisplayName()));
    }

    public String activeViewerPage() {
        Optional<Show> show = this.showRepository.findByShowSubdomain(authUtil.tokenDTO.getShowSubdomain());
        if(show.isPresent() && show.get().getPages() != null) {
            Optional<Page> activeViewerPage = show.get().getPages().stream().filter(Page::getActive).findFirst();
            if(activeViewerPage.isPresent()) {
                return activeViewerPage.get().getHtml();
            }
            return "";
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }

    private List<Sequence> processSequencesForViewer(Show show) {
        List<Sequence> updatedSequences = show.getSequences();
        List<SequenceGroup> updatedSequenceGroups = show.getSequenceGroups();
        updatedSequences = this.sortAndFilterSequences(updatedSequences);
        updatedSequenceGroups = this.filterSequenceGroups(updatedSequenceGroups);
        return this.replaceSequencesWithSequenceGroups(updatedSequences, updatedSequenceGroups);
    }

    private List<Sequence> sortAndFilterSequences(List<Sequence> sequences) {
        sequences.sort(Comparator.comparing(Sequence::getOrder));
        return sequences.stream()
                .filter(sequence -> sequence.getVisibilityCount() == 0)
                .filter(Sequence::getActive)
                .toList();
    }

    private List<SequenceGroup> filterSequenceGroups(List<SequenceGroup> sequenceGroups) {
        return sequenceGroups.stream()
                .filter(group -> group.getVisibilityCount() == 0)
                .toList();
    }

    private List<Sequence> replaceSequencesWithSequenceGroups(List<Sequence> sequences, List<SequenceGroup> sequenceGroups) {
        List<Sequence> sequencesWithGroups = new ArrayList<>();
        List<String> groupsAdded = new ArrayList<>();
        for(Sequence sequence: sequences) {
            if(StringUtils.isNotEmpty(sequence.getGroup())) {
                Optional<SequenceGroup> sequenceGroup = sequenceGroups.stream()
                        .filter(group -> StringUtils.equalsIgnoreCase(sequence.getGroup(), group.getName()))
                        .findFirst();
                if(sequenceGroup.isPresent() && !groupsAdded.contains(sequence.getGroup())) {
                    groupsAdded.add(sequence.getGroup());

                    sequence.setName(sequenceGroup.get().getName());
                    sequence.setDisplayName(sequenceGroup.get().getName());
                    sequence.setVisibilityCount(sequenceGroup.get().getVisibilityCount());

                    sequencesWithGroups.add(sequence);
                }
            }else {
                sequencesWithGroups.add(sequence);
            }
        }
        return sequencesWithGroups;
    }
}
