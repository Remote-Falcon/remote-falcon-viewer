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

  public Optional<Show> findByShowSubdomainForViewer(String showSubdomain) {
    // Exclude large fields that viewers don't need
    Show result = mongoCollection()
        .find(Filters.eq("showSubdomain", showSubdomain))
        .projection(
            com.mongodb.client.model.Projections.exclude(
                "stats.page",              // Page stats can be huge
                "stats.voting",            // Voting stats not needed by viewers
                "stats.votingWin",         // Voting win stats not needed
                "stats.jukebox",           // Jukebox stats not needed
                "showToken",               // Sensitive authentication token
                "email",                   // Sensitive PII
                "password",                // Sensitive
                "lastLoginIp",             // Sensitive field
                "lastLoginDate",           // Not needed
                "passwordResetLink",       // Sensitive
                "passwordResetExpiry",     // Not needed
                "apiAccess",               // Not needed by viewers
                "userProfile",             // Not needed by viewers
                "showNotifications",       // Not needed by viewers
                "activeViewers"            // Contains other viewers' IP addresses (PII)
            )
        )
        .first();
    return Optional.ofNullable(result);
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

  public void appendPageStat(String showSubdomain, Stat.Page stat) {
    mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("stats.page", stat));
  }

  public void incrementVoteAndAppendVoter(String showSubdomain, String sequenceName, String voterIp, java.time.LocalDateTime voteTime, Stat.Voting votingStat) {
    if (votingStat != null) {
      mongoCollection().updateOne(
          Filters.and(
              Filters.eq("showSubdomain", showSubdomain),
              Filters.eq("votes.sequence.name", sequenceName)
          ),
          Updates.combine(
              Updates.inc("votes.$.votes", 1),
              Updates.push("votes.$.viewersVoted", voterIp),
              Updates.set("votes.$.lastVoteTime", voteTime),
              Updates.push("stats.voting", votingStat)
          )
      );
    } else {
      mongoCollection().updateOne(
          Filters.and(
              Filters.eq("showSubdomain", showSubdomain),
              Filters.eq("votes.sequence.name", sequenceName)
          ),
          Updates.combine(
              Updates.inc("votes.$.votes", 1),
              Updates.push("votes.$.viewersVoted", voterIp),
              Updates.set("votes.$.lastVoteTime", voteTime)
          )
      );
    }
  }

  public void addNewVoteAndStat(String showSubdomain, com.remotefalcon.library.models.Vote vote, Stat.Voting votingStat) {
    if (votingStat != null) {
      mongoCollection().updateOne(
          Filters.eq("showSubdomain", showSubdomain),
          Updates.combine(
              Updates.push("votes", vote),
              Updates.push("stats.voting", votingStat)
          )
      );
    } else {
      mongoCollection().updateOne(
          Filters.eq("showSubdomain", showSubdomain),
          Updates.push("votes", vote)
      );
    }
  }

  public void incrementSequenceGroupVoteAndAppendVoter(String showSubdomain, String groupName, String voterIp, java.time.LocalDateTime voteTime, Stat.Voting votingStat) {
    mongoCollection().updateOne(
        Filters.and(
            Filters.eq("showSubdomain", showSubdomain),
            Filters.eq("votes.sequenceGroup.name", groupName)
        ),
        Updates.combine(
            Updates.inc("votes.$.votes", 1),
            Updates.push("votes.$.viewersVoted", voterIp),
            Updates.set("votes.$.lastVoteTime", voteTime),
            Updates.push("stats.voting", votingStat)
        )
    );
  }

  public void updateActiveViewer(String showSubdomain, String ipAddress, java.time.LocalDateTime visitTime) {
    // First, remove any existing entry with this IP
    mongoCollection().updateOne(
        Filters.eq("showSubdomain", showSubdomain),
        Updates.pull("activeViewers", Filters.eq("ipAddress", ipAddress))
    );

    // Then add the new entry
    mongoCollection().updateOne(
        Filters.eq("showSubdomain", showSubdomain),
        Updates.push("activeViewers",
            com.remotefalcon.library.models.ActiveViewer.builder()
                .ipAddress(ipAddress)
                .visitDateTime(visitTime)
                .build()
        )
    );
  }

  public void updatePlayingNow(String showSubdomain, String playingNow) {
    mongoCollection().updateOne(
        Filters.eq("showSubdomain", showSubdomain),
        Updates.set("playingNow", playingNow)
    );
  }

  public void updatePlayingNext(String showSubdomain, String playingNext) {
    mongoCollection().updateOne(
        Filters.eq("showSubdomain", showSubdomain),
        Updates.set("playingNext", playingNext)
    );
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

  public long appendPageStatIfNotOwner(String showSubdomain, String clientIp, Stat.Page stat) {
    // Only append stat if clientIp is different from lastLoginIp (owner's IP)
    var result = mongoCollection().updateOne(
        Filters.and(
            Filters.eq("showSubdomain", showSubdomain),
            Filters.ne("lastLoginIp", clientIp)
        ),
        Updates.push("stats.page", stat)
    );
    return result.getModifiedCount();
  }
}
