package com.remotefalcon.controller;

import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.service.GraphQLMutationService;
import com.remotefalcon.service.GraphQLQueryService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.graphql.*;

import java.time.LocalDateTime;

@JBossLog
@GraphQLApi
@ApplicationScoped
public class GraphQLController {
    @Inject
    GraphQLQueryService graphQLQueryService;

    @Inject
    GraphQLMutationService graphQLMutationService;

    /********
     Mutations
     ********/
    @Mutation
    @Description("Insert Viewer Page Stats")
    public Boolean insertViewerPageStats(String showSubdomain, LocalDateTime date) {
        return graphQLMutationService.insertViewerPageStats(showSubdomain, date);
    }

    @Mutation
    @Description("Update Active Viewers")
    public Boolean updateActiveViewers(String showSubdomain) {
        return graphQLMutationService.updateActiveViewers(showSubdomain);
    }

    @Mutation
    @Description("Update Playing Now")
    public Boolean updatePlayingNow(String showSubdomain, String playingNow) {
        return graphQLMutationService.updatePlayingNow(showSubdomain, playingNow);
    }

    @Mutation
    @Description("Update Playing Next")
    public Boolean updatePlayingNext(String showSubdomain, String playingNext) {
        return graphQLMutationService.updatePlayingNext(showSubdomain, playingNext);
    }

    @Mutation
    @Name("addSequenceToQueue")
    @Description("Add Sequence To Queue")
    public Boolean addSequenceToQueue(String showSubdomain, String name, Double latitude, Double longitude) {
        return graphQLMutationService.addSequenceToQueue(showSubdomain, name, latitude, longitude);
    }

    @Mutation
    @Name("voteForSequence")
    @Description("Vote For Sequence")
    public Boolean voteForSequence(String showSubdomain, String name, Double latitude, Double longitude) {
        return graphQLMutationService.voteForSequence(showSubdomain, name, latitude, longitude);
    }

    /*******
     Queries
     *******/
    @Query
    @Name("getShow")
    @Description("Get Show")
    public Show getShow(String showSubdomain) {
        return this.graphQLQueryService.getShow(showSubdomain);
    }

    @Query
    @Name("getActiveViewerPage")
    @Description("Get Active Viewer Page")
    public String activeViewerPage(String showSubdomain) {
        return graphQLQueryService.activeViewerPage(showSubdomain);
    }
}
