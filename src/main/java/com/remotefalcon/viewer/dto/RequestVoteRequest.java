package com.remotefalcon.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestVoteRequest {
    private Float viewerLatitude;
    private Float viewerLongitude;
    private String timezone;
    private ZonedDateTime date;
    private String sequence;
    private String showSubdomain;
}
