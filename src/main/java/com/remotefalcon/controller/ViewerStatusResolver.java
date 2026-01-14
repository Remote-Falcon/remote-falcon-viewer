package com.remotefalcon.controller;

import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.service.ViewerStatusService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Source;

@GraphQLApi
@ApplicationScoped
public class ViewerStatusResolver {
  @Inject
  ViewerStatusService viewerStatusService;

  @Name("viewerStatus")
  @Description("Viewer request/vote status for the show")
  public String viewerStatus(@Source Show show) {
    return viewerStatusService.buildViewerStatus(show);
  }
}
