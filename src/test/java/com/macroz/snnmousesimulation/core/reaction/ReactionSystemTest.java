package com.macroz.snnmousesimulation.core.reaction;

import com.macroz.snnmousesimulation.core.reaction.concrete.InjectDopamineReaction;
import com.macroz.snnmousesimulation.world.AgentEffects;
import com.macroz.snnmousesimulation.world.BoundaryHit;
import com.macroz.snnmousesimulation.world.FoodEaten;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactionSystemTest {

    // Reaction that reads data of a specific event type
    private static final class RecordSide implements ReactionStrategy<BoundaryHit> {
        private final List<BoundaryHit.Side> sides = new ArrayList<>();

        @Override
        public Class<BoundaryHit> eventClass() {
            return BoundaryHit.class;
        }

        @Override
        public void react(BoundaryHit event, AgentEffects effects) {
            sides.add(event.side());
        }
    }

    private static final class RecordDopamine implements AgentEffects {
        private double dopamine;

        @Override
        public void move(double distance, double rotationRadians) {
        }

        @Override
        public void injectDopamine(double amount) {
            dopamine += amount;
        }
    }

    @Test
    void passesEachEventOnlyToReactionsBoundToItsType() {
        RecordSide recordSide = new RecordSide();
        ReactionSystem reactions = new ReactionSystem();
        reactions.addReaction("Reward", FoodEaten.class, new InjectDopamineReaction(5.0));
        reactions.addReaction("Wall", BoundaryHit.class, recordSide);
        RecordDopamine effects = new RecordDopamine();

        reactions.processEvents(List.of(new FoodEaten(1.0), new BoundaryHit(1.0, BoundaryHit.Side.LEFT)), effects);

        assertEquals(5.0, effects.dopamine);
        assertEquals(List.of(BoundaryHit.Side.LEFT), recordSide.sides);
    }

    @Test
    void rejectsReactionBoundToEventItCannotHandle() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> new ReactionSystem().addReaction("Wall", FoodEaten.class, new RecordSide()));
        assertTrue(e.getMessage().contains("'Wall'"), e.getMessage());
    }
}
