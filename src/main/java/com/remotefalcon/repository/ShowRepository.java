package com.remotefalcon.repository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.remotefalcon.library.models.Request;
import com.remotefalcon.library.models.Stat;
import com.remotefalcon.library.quarkus.entity.Show;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ShowRepository implements PanacheMongoRepository<Show> {
  public Optional<Show> findByShowSubdomain(String showSubdomain) {
    return find("showSubdomain", showSubdomain).firstResultOptional();
  }

  public long nextRequestPosition(Show show) {
    if (show == null || show.getRequests() == null || show.getRequests().isEmpty()) {
      return 1L;
    }

    // Find the maximum position in the existing requests and add 1
    return show.getRequests().stream()
        .mapToInt(Request::getPosition)
        .max()
        .orElse(0) + 1;
  }

  /**
   * Allocates a block of positions at once.
   * Returns the starting position. Caller can use startPos, startPos+1, startPos+2, etc.
   * @param show the show object
   * @param count how many positions to allocate
   * @return the starting position of the allocated block
   */
  public long allocatePositionBlock(Show show, int count) {
    if (show == null || show.getRequests() == null || show.getRequests().isEmpty()) {
      return 1L;
    }

    // Find the maximum position in the existing requests and add 1 to get the starting position
    int maxPosition = show.getRequests().stream()
        .mapToInt(Request::getPosition)
        .max()
        .orElse(0);

    return maxPosition + 1;
  }

  public void appendRequest(String showSubdomain, Request request) {
    mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("requests", request));
  }

  public void appendJukeboxStat(String showSubdomain, Stat.Jukebox stat) {
    mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("stats.jukebox", stat));
  }

  public void appendRequestAndJukeboxStat(String showSubdomain, Request request, Stat.Jukebox stat) {
    mongoCollection().updateOne(
        Filters.eq("showSubdomain", showSubdomain),
        Updates.combine(
            Updates.push("requests", request),
            Updates.push("stats.jukebox", stat)
        )
    );
  }

  public void appendMultipleRequestsAndJukeboxStat(String showSubdomain, java.util.List<Request> requests,
      Stat.Jukebox stat) {
    mongoCollection().updateOne(
        Filters.eq("showSubdomain", showSubdomain),
        Updates.combine(
            Updates.pushEach("requests", requests),
            Updates.push("stats.jukebox", stat)
        )
    );
  }
}
