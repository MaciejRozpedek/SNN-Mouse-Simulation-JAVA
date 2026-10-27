package com.macroz.snnmousesimulation.world;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.macroz.snnmousesimulation.api.SnnDiagnosticState;
import com.macroz.snnmousesimulation.core.SnnDiagnosticTracker;
import com.macroz.snnmousesimulation.core.SnnEngine;
import com.macroz.snnmousesimulation.core.SnnNetworkData;
import com.macroz.snnmousesimulation.core.input.InputConfig;
import com.macroz.snnmousesimulation.core.input.InputFrame;
import com.macroz.snnmousesimulation.core.input.InputSystem;
import com.macroz.snnmousesimulation.core.output.OutputConfig;
import com.macroz.snnmousesimulation.core.output.OutputSystem;
import com.macroz.snnmousesimulation.core.reaction.ReactionConfig;
import com.macroz.snnmousesimulation.core.reaction.ReactionSystem;
import com.macroz.snnmousesimulation.registry.Registries;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class Agent implements AgentBody, AgentEffects {
    @Setter
    private double x;
    @Setter
    private double y;
    @Setter
    private double angle;
    // SNN instance
    @JsonIgnore
    private final SnnEngine engine;
    @JsonIgnore
    private final SnnDiagnosticTracker diagnosticTracker;
    @JsonIgnore
    private final InputSystem inputSystem;
    @JsonIgnore
    private final OutputSystem outputSystem;
    @JsonIgnore
    private final ReactionSystem reactionSystem;
    @Getter(AccessLevel.NONE)
    private final List<AgentEvent> pendingEvents = new ArrayList<>();

    public Agent(double startX, double startY, double startAngle, SnnNetworkData data) {
        this.x = startX;
        this.y = startY;
        this.angle = startAngle;
        // SNN initialization
        this.engine = new SnnEngine(data);
        this.diagnosticTracker = new SnnDiagnosticTracker(engine);
        this.inputSystem = new InputSystem();
        this.outputSystem = new OutputSystem();
        this.reactionSystem = new ReactionSystem();

        for (InputConfig config : data.inputConfigs()) {
            inputSystem.addInput(config.targetNeuronIndices(),
                    Registries.INPUTS.create(config.sensorType(), config.params()));
        }

        for (OutputConfig config : data.outputConfigs()) {
            outputSystem.addOutput(config.sourceNeuronIndices(),
                    Registries.OUTPUTS.create(config.outputType(), config.params()));
        }

        for (ReactionConfig config : data.reactionConfigs()) {
            reactionSystem.addReaction(config.name(), Registries.EVENTS.resolve(config.event()),
                    Registries.REACTIONS.create(config.reactionType(), config.params()));
        }
    }

    public void update(InputFrame frame) {
        // 1. EVENTS - React to events from the previous step
        reactionSystem.processEvents(frame.events(), this);

        // 2. SENSORS - Get sensory inputs and convert to currents
        var registeredInputs = inputSystem.calculateFrameInputs(frame);
        for (var input : registeredInputs) {
            engine.addInputCurrent(input.indices(), input.currents());
        }

        // 3. BRAIN PROCESS - Run one time step
        List<Integer> firedIndices = engine.step(frame.deltaTimeMs());
        diagnosticTracker.registerStep(frame.deltaTimeMs(), firedIndices);

        // 4. RESPONSE - Interpret outputs and act
        outputSystem.processOutputs(this, firedIndices, frame.deltaTimeMs());
    }

    @Override
    public void postEvent(AgentEvent event) {
        pendingEvents.add(event);
    }

    // Returns the events posted since the last call
    public List<AgentEvent> drainEvents() {
        List<AgentEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    public SnnDiagnosticState getSnnDiagnostics() {
        return diagnosticTracker.snapshot();
    }

    @Override
    public void injectDopamine(double amount) {
        engine.injectDopamine(amount);
    }

    @Override
    public void move(double distance, double rotationRadians) {
        this.angle += rotationRadians;
        this.x += Math.cos(this.angle) * distance;
        this.y += Math.sin(this.angle) * distance;
    }
}
