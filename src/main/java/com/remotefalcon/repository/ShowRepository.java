package com.remotefalcon.repository;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.UpdateResult;
import com.remotefalcon.library.models.ActiveViewer;
import com.remotefalcon.library.models.Request;
import com.remotefalcon.library.models.Stat;
import com.remotefalcon.library.models.Vote;
import com.remotefalcon.library.quarkus.entity.Show;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ShowRepository implements PanacheMongoRepository<Show> {
    public Optional<Show> findByShowSubdomain(String showSubdomain) {
        return find("showSubdomain", showSubdomain).firstResultOptional();
    }

    public void appendPageStat(String showSubdomain, Stat.Page pageStat) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("stats.page", pageStat));
    }

    public void refreshActiveViewer(String showSubdomain, ActiveViewer viewer, String ownerIp) {
        Bson filter = Filters.eq("showSubdomain", showSubdomain);
        if (ownerIp != null) {
            filter = Filters.and(filter,
                    Filters.or(Filters.exists("lastLoginIp", false), Filters.ne("lastLoginIp", viewer.getIpAddress())));
        }
        mongoCollection().updateOne(filter, Updates.combine(
                Updates.pull("activeViewers", new Document("ipAddress", viewer.getIpAddress())),
                Updates.push("activeViewers", viewer)
        ));
    }

    public void updatePlayingNow(String showSubdomain, String playingNow) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.set("playingNow", playingNow));
    }

    public void updatePlayingNext(String showSubdomain, String playingNext) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.set("playingNext", playingNext));
    }

    public long nextRequestPosition(String showSubdomain) {
        while(true) {
            Document updated = mongoCollection().withDocumentClass(Document.class).findOneAndUpdate(
                    Filters.eq("showSubdomain", showSubdomain),
                    Updates.inc("requestPositionCursor", 1L),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            );
            if(updated == null) {
                return 1L;
            }
            long cursorValue = 1L;
            Object cursor = updated.get("requestPositionCursor");
            if(cursor instanceof Number number) {
                cursorValue = number.longValue();
            }
            List<Document> requests = updated.getList("requests", Document.class);
            long maxPosition = 0L;
            if(requests != null && !requests.isEmpty()) {
                maxPosition = requests.stream()
                        .map(document -> document.get("position"))
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .mapToLong(Number::longValue)
                        .max()
                        .orElse(0L);
            }
            if(cursorValue > maxPosition) {
                return cursorValue;
            }
            long desired = maxPosition + 1;
            UpdateResult adjusted = mongoCollection().updateOne(
                    Filters.and(
                            Filters.eq("showSubdomain", showSubdomain),
                            Filters.eq("requestPositionCursor", cursorValue)
                    ),
                    Updates.set("requestPositionCursor", desired)
            );
            if(adjusted.getModifiedCount() > 0) {
                return desired;
            }
        }
    }

    public void appendRequest(String showSubdomain, Request request) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("requests", request));
    }

    public void appendJukeboxStat(String showSubdomain, Stat.Jukebox stat) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("stats.jukebox", stat));
    }

    public boolean incrementSequenceVote(String showSubdomain, String sequenceName, int increment, String voterIp, LocalDateTime timestamp) {
        UpdateResult result = mongoCollection().updateOne(
                Filters.eq("showSubdomain", showSubdomain),
                Updates.combine(
                        Updates.inc("votes.$[vote].votes", increment),
                        Updates.push("votes.$[vote].viewersVoted", voterIp),
                        Updates.set("votes.$[vote].lastVoteTime", timestamp),
                        Updates.set("votes.$[vote].ownerVoted", Boolean.FALSE)
                ),
                new UpdateOptions().arrayFilters(List.of(Filters.eq("vote.sequence.name", sequenceName)))
        );
        return result.getModifiedCount() > 0;
    }

    public boolean incrementSequenceGroupVote(String showSubdomain, String groupName, String voterIp, LocalDateTime timestamp) {
        UpdateResult result = mongoCollection().updateOne(
                Filters.eq("showSubdomain", showSubdomain),
                Updates.combine(
                        Updates.inc("votes.$[vote].votes", 1),
                        Updates.push("votes.$[vote].viewersVoted", voterIp),
                        Updates.set("votes.$[vote].lastVoteTime", timestamp),
                        Updates.set("votes.$[vote].ownerVoted", Boolean.FALSE)
                ),
                new UpdateOptions().arrayFilters(List.of(Filters.eq("vote.sequenceGroup.name", groupName)))
        );
        return result.getModifiedCount() > 0;
    }

    public void appendVote(String showSubdomain, Vote vote) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("votes", vote));
    }

    public void appendVotingStat(String showSubdomain, Stat.Voting stat) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("stats.voting", stat));
    }

    public void updatePsaLastPlayed(String showSubdomain, String psaName, LocalDateTime timestamp) {
        mongoCollection().updateOne(
                Filters.eq("showSubdomain", showSubdomain),
                Updates.set("psaSequences.$[psa].lastPlayed", timestamp),
                new UpdateOptions().arrayFilters(List.of(Filters.eq("psa.name", psaName)))
        );
    }
}



