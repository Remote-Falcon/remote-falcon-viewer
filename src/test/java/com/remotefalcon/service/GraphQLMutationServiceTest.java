package com.remotefalcon.service;

import com.remotefalcon.exception.CustomGraphQLExceptionResolver;
import com.remotefalcon.library.models.ActiveViewer;
import com.remotefalcon.library.models.Stat;
import com.remotefalcon.library.quarkus.entity.Show;
import com.remotefalcon.repository.ShowRepository;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class GraphQLMutationServiceTest {

    @Inject
    GraphQLMutationService service;

    @InjectMock
    ShowRepository showRepository;

    @InjectMock
    RoutingContext routingContext;

    @InjectMock
    HttpServerRequest httpServerRequest;

    @BeforeEach
    void setUp() {
        reset(showRepository, routingContext, httpServerRequest);
        when(routingContext.request()).thenReturn(httpServerRequest);
        when(httpServerRequest.getHeader("CF-Connecting-IP")).thenReturn("1.2.3.4");
    }

    private Show mockShowWithStatsAndActiveViewers(String lastLoginIp) {
        Show show = mock(Show.class);
        when(show.getLastLoginIp()).thenReturn(lastLoginIp);

        // Mock Stats with a real list for page stats
        Stat stats = mock(Stat.class);
        when(stats.getPage()).thenReturn(new ArrayList<>());
        when(stats.getJukebox()).thenReturn(new ArrayList<>());
        when(stats.getVoting()).thenReturn(new ArrayList<>());
        when(show.getStats()).thenReturn(stats);

        // Active viewers list
        List<ActiveViewer> activeViewers = new ArrayList<>();
        when(show.getActiveViewers()).thenReturn(activeViewers);

        return show;
    }

    @Nested
    @DisplayName("insertViewerPageStats")
    class InsertViewerPageStatsTests {
        @Test
        @DisplayName("Should add page stat and persist when show exists and IP differs from last login")
        void shouldInsertViewerPageStats() {
            // Given
            Show show = mockShowWithStatsAndActiveViewers("9.9.9.9");
            when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

            // When
            Boolean result = service.insertViewerPageStats("test", LocalDateTime.now());

            // Then
            assertTrue(result);
            // Ensure a page stat was added
            assertEquals(1, show.getStats().getPage().size());
            verify(showRepository).persistOrUpdate(any(Show.class));
        }

        @Test
        @DisplayName("Should return true but not persist when IP equals last login IP")
        void shouldReturnTrueButNotPersistWhenSameIp() {
            // Given
            Show show = mockShowWithStatsAndActiveViewers("1.2.3.4");
            when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

            // When
            Boolean result = service.insertViewerPageStats("test", LocalDateTime.now());

            // Then
            assertTrue(result);
            assertEquals(0, show.getStats().getPage().size());
            verify(showRepository, never()).persistOrUpdate(any(Show.class));
        }

        @Test
        @DisplayName("Should throw when show not found")
        void shouldThrowWhenShowNotFound() {
            // Given
            when(showRepository.findByShowSubdomain("missing")).thenReturn(Optional.empty());

            // When / Then
            assertThrows(CustomGraphQLExceptionResolver.class, () -> service.insertViewerPageStats("missing", LocalDateTime.now()));
        }
    }

    @Nested
    @DisplayName("updateActiveViewers")
    class UpdateActiveViewersTests {
        @Test
        @DisplayName("Should add active viewer and persist when IP differs from last login and remove existing duplicate")
        void shouldUpdateActiveViewers() {
            // Given
            Show show = mockShowWithStatsAndActiveViewers("9.9.9.9");
            // Existing viewer with same IP that should be removed
            ActiveViewer existing = mock(ActiveViewer.class);
            when(existing.getIpAddress()).thenReturn("1.2.3.4");
            show.getActiveViewers().add(existing);

            when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

            // When
            Boolean result = service.updateActiveViewers("test");

            // Then
            assertTrue(result);
            // The old one removed and new one added -> size stays 1
            assertEquals(1, show.getActiveViewers().size());
            verify(showRepository).persistOrUpdate(show);
        }

        @Test
        @DisplayName("Should not persist when IP equals last login IP")
        void shouldNotPersistWhenSameIpAsLastLogin() {
            // Given
            Show show = mockShowWithStatsAndActiveViewers("1.2.3.4");
            when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

            // When
            Boolean result = service.updateActiveViewers("test");

            // Then
            assertTrue(result);
            verify(showRepository, never()).persistOrUpdate(any(Show.class));
        }

        @Test
        @DisplayName("Should throw when show not found for active viewers update")
        void shouldThrowWhenShowNotFound() {
            // Given
            when(showRepository.findByShowSubdomain("missing")).thenReturn(Optional.empty());

            // When / Then
            assertThrows(CustomGraphQLExceptionResolver.class, () -> service.updateActiveViewers("missing"));
        }
    }

    @Nested
    @DisplayName("updatePlayingNow")
    class UpdatePlayingNowTests {
        @Test
        @DisplayName("Should update playing now and persist")
        void shouldUpdatePlayingNow() {
            // Given
            Show show = mockShowWithStatsAndActiveViewers("9.9.9.9");
            when(showRepository.findByShowSubdomain("test")).thenReturn(Optional.of(show));

            // When
            Boolean result = service.updatePlayingNow("test", "Song A");

            // Then
            assertTrue(result);
            verify(show).setPlayingNow("Song A");
            verify(showRepository).persistOrUpdate(show);
        }

        @Test
        @DisplayName("Should throw when show not found for playing now update")
        void shouldThrowPlayingNowNotFound() {
            // Given
            when(showRepository.findByShowSubdomain("missing")).thenReturn(Optional.empty());

            // When / Then
            assertThrows(CustomGraphQLExceptionResolver.class, () -> service.updatePlayingNow("missing", "Song A"));
        }
    }
}
