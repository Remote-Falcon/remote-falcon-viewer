package com.remotefalcon.service;

import com.remotefalcon.library.enums.StatusResponse;
import com.remotefalcon.library.models.Request;
import com.remotefalcon.library.models.Sequence;
import com.remotefalcon.library.models.SequenceGroup;
import com.remotefalcon.library.models.ViewerPage;
import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.repository.ShowRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@JBossLog
@ApplicationScoped
public class GraphQLQueryService {
    @Inject
    ShowRepository showRepository;

    public Show getShow(String showSubdomain) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent()) {
            Show existingShow = show.get();
            this.updatePlayingNow(existingShow);
            this.updatePlayingNext(existingShow);
            existingShow.setSequences(this.processSequencesForViewer(existingShow));
        }
        return show.orElse(null);
    }

    private void updatePlayingNow(Show show) {
        Optional<Sequence> playingNowSequence = show.getSequences().stream()
                .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), show.getPlayingNow()))
                .findFirst();
        playingNowSequence.ifPresent(sequence -> {
            show.setPlayingNow(sequence.getDisplayName());
            show.setPlayingNowSequence(sequence);
        });
    }

    private void updatePlayingNext(Show show) {
        //Get next from request list
        Optional<Request> nextRequest = show.getRequests().stream()
                .min(Comparator.comparing(Request::getPosition));
        nextRequest.ifPresent(request -> {
            show.setPlayingNext(request.getSequence().getDisplayName());
            show.setPlayingNextSequence(request.getSequence());
        });

        //Get next from schedule if next request is empty
        if(nextRequest.isEmpty()) {
            Optional<Sequence> playingNextScheduledSequence = show.getSequences().stream()
                    .filter(sequence -> StringUtils.equalsIgnoreCase(sequence.getName(), show.getPlayingNextFromSchedule()))
                    .findFirst();
            playingNextScheduledSequence.ifPresent(sequence -> {
                show.setPlayingNext(sequence.getDisplayName());
                show.setPlayingNextSequence(sequence);
            });
        }
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

    public String activeViewerPage(String showSubdomain) {
        Optional<Show> show = this.showRepository.findByShowSubdomain(showSubdomain);
        if(show.isPresent() && show.get().getPages() != null) {
            Show existingShow = show.get();
            Optional<ViewerPage> activeViewerPage = existingShow.getPages().stream().filter(ViewerPage::getActive).findFirst();
            if(activeViewerPage.isPresent()) {
                return activeViewerPage.get().getHtml();
            }
            return "";
        }
        throw new RuntimeException(StatusResponse.UNEXPECTED_ERROR.name());
    }
}
