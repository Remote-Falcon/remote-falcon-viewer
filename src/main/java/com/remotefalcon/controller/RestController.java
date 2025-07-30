package com.remotefalcon.controller;

import com.remotefalcon.exception.CustomGraphQLExceptionResolver;
import com.remotefalcon.request.RequestVoteRequest;
import com.remotefalcon.response.RequestVoteResponse;
import com.remotefalcon.service.GraphQLMutationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

@ApplicationScoped
@Path("/")
public class RestController {
    @Inject
    GraphQLMutationService graphQLMutationService;

    @POST
    @Path("/addSequenceToQueue")
    @Consumes(MediaType.APPLICATION_JSON)
    public RequestVoteResponse addSequenceToQueue(RequestVoteRequest request) {
        try {
            this.graphQLMutationService.addSequenceToQueue(
                    request.getShowSubdomain(),
                    request.getSequence(),
                    request.getViewerLatitude().doubleValue(),
                    request.getViewerLongitude().doubleValue()
            );
            return RequestVoteResponse.builder().build();
        }catch (CustomGraphQLExceptionResolver e) {
            return RequestVoteResponse.builder()
                    .message(e.getExtensions().get("message").toString())
                    .build();
        }
    }

    @POST
    @Path("/voteForSequence")
    @Consumes(MediaType.APPLICATION_JSON)
    public RequestVoteResponse voteForSequence(RequestVoteRequest request) {
        try {
            this.graphQLMutationService.voteForSequence(
                    request.getShowSubdomain(),
                    request.getSequence(),
                    request.getViewerLatitude().doubleValue(),
                    request.getViewerLongitude().doubleValue()
            );
            return RequestVoteResponse.builder().build();
        }catch (CustomGraphQLExceptionResolver e) {
            return RequestVoteResponse.builder()
                    .message(e.getExtensions().get("message").toString())
                    .build();
        }
    }
}
