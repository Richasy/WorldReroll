package dev.richasy.worldreroll;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RerollSessionTest {
    @Test void cannotDeleteBeforeReplacementIsPlayable() {
        var session = new RerollSession();
        assertFalse(session.ready("new-world", true));
        session.loading("new-world");
        assertFalse(session.ready("old-world", true));
        assertFalse(session.ready("new-world", false));
        assertEquals(RerollSession.Phase.LOADING, session.phase());
        assertTrue(session.ready("new-world", true));
        assertFalse(session.ready("new-world", true));
        session.complete();
        assertEquals(RerollSession.Phase.COMPLETE, session.phase());
    }

    @Test void cancellationAndFailuresNeverAuthorizeDeletion() {
        for (boolean started : new boolean[]{false, true}) {
            var session = new RerollSession();
            if (started) session.loading("new-world");
            session.cancel();
            assertFalse(session.ready("new-world", true));
            assertThrows(IllegalStateException.class, session::complete);
        }
    }

    @Test void duplicatePreparationIsRejected() {
        var session = new RerollSession();
        session.loading("new-world");
        assertThrows(IllegalStateException.class, () -> session.loading("other-world"));
    }
}
