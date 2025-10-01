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
            return cursorValue;
        }
    }

    public void appendRequest(String showSubdomain, Request request) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("requests", request));
    }

    public void appendJukeboxStat(String showSubdomain, Stat.Jukebox stat) {
        mongoCollection().updateOne(Filters.eq("showSubdomain", showSubdomain), Updates.push("stats.jukebox", stat));
    }
}
