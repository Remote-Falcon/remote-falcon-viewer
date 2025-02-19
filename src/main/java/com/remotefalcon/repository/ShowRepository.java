package com.remotefalcon.repository;

import com.remotefalcon.library.quarkus.entity.Show;
import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ShowRepository implements PanacheMongoRepository<Show> {
    public Optional<Show> findByShowSubdomain(String showSubdomain) {
        return find("showSubdomain", showSubdomain).firstResultOptional();
    }
}
