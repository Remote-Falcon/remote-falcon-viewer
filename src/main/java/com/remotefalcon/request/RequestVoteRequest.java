package com.remotefalcon.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RequestVoteRequest {
  private Float viewerLatitude;
  private Float viewerLongitude;
  private String sequence;
  private String showSubdomain;
}