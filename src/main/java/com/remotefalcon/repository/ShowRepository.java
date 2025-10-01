package com.remotefalcon.repository;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.remotefalcon.library.models.Request;
import com.remotefalcon.library.models.Stat;
import com.remotefalcon.library.quarkus.entity.Show;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;

import java.util.Optional;

@ApplicationScoped
public class ShowRepository implements PanacheMongoRepository<Show> {
  public Optional<Show> findByShowSubdomain(String showSubdomain) {
    return find("showSubdomain", showSubdomain).firstResultOptional();
  }

  public long nextRequestPosition(String showSubdomain) {
    while (true) {
      Document updated = mongoCollection().withDocumentClass(Document.class).findOneAndUpdate(
          Filters.eq("showSubdomain", showSubdomain),
          Updates.inc("requestPositionCursor", 1L),
          new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
      if (updated == null) {
        return 1L;
      }
      long cursorValue = 1L;
      Object cursor = updated.get("requestPositionCursor");
      if (cursor instanceof Number number) {
        cursorValue = number.longValue();
      }
      return cursorValue;
    }
  }

  /**
   * Allocates a block of positions at once to reduce DB contention.
   * Returns the starting position. Caller can use startPos, startPos+1, startPos+2, etc.
   * @param showSubdomain the show subdomain
   * @param count how many positions to allocate
   * @return the starting position of the allocated block
   */
  public long allocatePositionBlock(String showSubdomain, int count) {
    while (true) {
      Document updated = mongoCollection().withDocumentClass(Document.class).findOneAndUpdate(
          Filters.eq("showSubdomain", showSubdomain),
          Updates.inc("requestPositionCursor", (long) count),
          new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
      if (updated == null) {
        return 1L;
      }
      long cursorValue = (long) count;
      Object cursor = updated.get("requestPositionCursor");
      if (cursor instanceof Number number) {
        cursorValue = number.longValue();
      }
      // Return the starting position (cursorValue - count + 1)
      return cursorValue - count + 1;
    }
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
