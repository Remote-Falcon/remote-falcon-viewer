package com.remotefalcon.viewer.controller;

import com.remotefalcon.viewer.aop.RequiresAPIAccess;
import com.remotefalcon.viewer.aop.RequiresAccess;
import com.remotefalcon.viewer.dto.RequestVoteRequest;
import com.remotefalcon.viewer.dto.RequestVoteResponse;
import com.remotefalcon.viewer.service.GraphQLMutationService;
import com.remotefalcon.viewer.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ViewerController {
    private final AuthUtil authUtil;
    private final GraphQLMutationService graphQLMutationService;

    @PostMapping(value = "/addSequenceToQueue")
    @RequiresAPIAccess
    public ResponseEntity<RequestVoteResponse> addSequenceToQueue(@RequestBody RequestVoteRequest requestVoteRequest) {
        try {
            Boolean success = this.graphQLMutationService.addSequenceToQueue(requestVoteRequest.getSequence(), requestVoteRequest.getViewerLatitude(), requestVoteRequest.getViewerLongitude());
            if(success) {
                return ResponseEntity.ok().build();
            }
        }catch (RuntimeException re) {
            return ResponseEntity.status(400).body(RequestVoteResponse.builder().message(re.getMessage()).build());
        }
        return ResponseEntity.status(400).build();
    }

    @PostMapping(value = "/voteForSequence")
    @RequiresAPIAccess
    public ResponseEntity<RequestVoteResponse> voteForSequence(@RequestBody RequestVoteRequest requestVoteRequest) {
        try {
            Boolean success = this.graphQLMutationService.voteForSequence(requestVoteRequest.getSequence(), requestVoteRequest.getViewerLatitude(), requestVoteRequest.getViewerLongitude());
            if(success) {
                return ResponseEntity.ok().build();
            }
        }catch (RuntimeException re) {
            return ResponseEntity.status(400).body(RequestVoteResponse.builder().message(re.getMessage()).build());
        }
        return ResponseEntity.status(400).build();
    }
}
