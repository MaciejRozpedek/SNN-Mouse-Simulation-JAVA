/**
 * @typedef {Object} AgentState
 * @property {number} x
 * @property {number} y
 * @property {number} angle
 */

/**
 * @typedef {Object} FoodState
 * @property {number} x
 * @property {number} y
 */

/**
 * @typedef {Object} SimulationState
 * @property {AgentState} agent
 * @property {FoodState[]} food
 * @property {number} simulationTimeMs
 * @property {SnnDiagnosticState} snnDiagnostics
 */

/**
 * @typedef {Object} SnnDiagnosticState
 * @property {number} dopamineLevel
 * @property {number} dopamineBaseLevel
 * @property {number} meanFiringRateHz
 * @property {number} totalSpikesInLastStep
 * @property {number[]} firedNeuronIndices
 * @property {number} averageWeight
 * @property {number} minWeight
 * @property {number} maxWeight
 * @property {number[]|null} neuronPotentials
 */

const canvas = document.getElementById('simCanvas');
const ctx = canvas.getContext('2d');
const snnCanvas = document.getElementById('snnCanvas');
const snnCtx = snnCanvas.getContext('2d');
const toggleBtn = document.getElementById('toggleBtn');
const reloadBtn = document.getElementById('reloadBtn');
const toggleNetBtn = document.getElementById('toggleNetBtn');
const elMouseX = document.getElementById('mouseX');
const elMouseY = document.getElementById('mouseY');
const elFoodCount = document.getElementById('foodCount');
const elSimTime = document.getElementById('simTime');
const elDopamine = document.getElementById('dopamineVal');
const elDopamineBase = document.getElementById('dopamineBaseVal');
const elMeanHz = document.getElementById('meanHz');
const elSpikesLastStep = document.getElementById('spikesLastStep');
const elAvgWeight = document.getElementById('avgWeight');
const elWeightRange = document.getElementById('weightRange');

const speedRange = document.getElementById('speedRange');
const speedValue = document.getElementById('speedValue');

const SPIKE_WINDOW_MS = 1_000;
// Multiple spikes in the same 5 ms/neuron cell are visually indistinguishable.
// Store one bit per cell so pathological activity cannot grow rendering work.
const SPIKE_ACTIVITY_BIN_MS = 5;
const SPIKE_ACTIVITY_BIN_COUNT = Math.ceil(SPIKE_WINDOW_MS / SPIKE_ACTIVITY_BIN_MS);
const SPIKE_ACTIVITY_FRAME_INTERVAL_MS = 1_000 / 60;
const DEFAULT_NEURON_COUNT = 16;
const MIN_SPIKE_ACTIVITY_HEIGHT = 170;
const MAX_SPIKE_ACTIVITY_HEIGHT = 520;
const SPIKE_ACTIVITY_VERTICAL_PADDING = 48;

/** @type {Map<number, Uint8Array>} */
let spikeBins = new Map();
let lastSpikeTimeMs = null;
let latestSpikeBin = null;
let spikeActivityNeuronCount = DEFAULT_NEURON_COUNT;
let spikeActivityDirty = true;
let lastSpikeActivityFrameTime = Number.NEGATIVE_INFINITY;
const spikeActivityBitmapCanvas = document.createElement('canvas');
const spikeActivityBitmapCtx = spikeActivityBitmapCanvas.getContext('2d');
let spikeActivityBitmap = null;

reloadBtn.addEventListener('click', () => {
    stopSimulation();
    latestWorldState = null;
    resetSpikeHistory(true);
    if (showSpikeActivity) drawSpikeActivity();

    fetch('/api/reload', { method: 'POST' })
        .then(() => {
            latestWorldState = null;
            resetSpikeHistory(true);
            if (showSpikeActivity) drawSpikeActivity();
            console.log("Simulation reloaded");
        })
        .catch(err => console.error("Failed to reload:", err));
});

speedRange.addEventListener('input', (e) => {
    speedValue.innerText = e.target.value;
});

speedRange.addEventListener('change', (e) => {
    fetch(`/api/speed?multiplier=${e.target.value}`, { method: 'POST' }).catch(console.error);
});

let eventSource = null;
let animationFrameId = null;
let isRunning = false;
let latestWorldState = null;
let showSpikeActivity = false;

toggleNetBtn.addEventListener('click', () => {
    showSpikeActivity = !showSpikeActivity;
    snnCanvas.style.display = showSpikeActivity ? 'block' : 'none';
    toggleNetBtn.textContent = showSpikeActivity ? 'Hide Spikes' : 'Show Spikes';

    if (showSpikeActivity) {
        spikeActivityDirty = true;
        drawSpikeActivity();
    }
});

function toggleSimulation() {
    if (isRunning) {
        stopSimulation();
    } else {
        startSimulation();
    }
}

function startSimulation() {
    if (isRunning) return;

    fetch('/api/start', { method: 'POST'}).then(() => {
        isRunning = true;
        toggleBtn.textContent = "Stop Simulation";
        toggleBtn.classList.add('stop');

        eventSource = new EventSource('/api/stream');
        
        eventSource.addEventListener('state', (event) => {
            latestWorldState = JSON.parse(event.data);
            bufferSpikes(latestWorldState);
        });
        
        eventSource.onerror = (err) => {
            console.error("SSE connection error:", err);
            if (!isRunning && eventSource) {
                eventSource.close();
                eventSource = null;
            }
        };

        renderLoop();
    }).catch(err => console.error("Failed to start:", err));
}

function stopSimulation() {
    if (!isRunning) return;

    fetch('/api/stop', { method: 'POST'}).then(() => {
        isRunning = false;
        toggleBtn.textContent = "Start Simulation";
        toggleBtn.classList.remove('stop');

        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }

        if (animationFrameId) {
            cancelAnimationFrame(animationFrameId);
            animationFrameId = null;
        }
    }).catch(err => console.error("Failed to stop:", err));
}

function renderLoop(frameTime = performance.now()) {
    if (!isRunning) return;

    if (latestWorldState) {
        render(latestWorldState);
        updateTelemetry(latestWorldState);
        updateSnnTelemetry(latestWorldState);
    }

    if (
        showSpikeActivity &&
        spikeActivityDirty &&
        frameTime - lastSpikeActivityFrameTime >= SPIKE_ACTIVITY_FRAME_INTERVAL_MS
    ) {
        drawSpikeActivity();
        lastSpikeActivityFrameTime = frameTime;
    }

    animationFrameId = requestAnimationFrame(renderLoop);
}

/**
 * @param {SimulationState} world
 */
function render(world) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Draw Food
    if (world.food) {
        world.food.forEach(f => {
            drawEntity(f.x, f.y, 10, '#22c55e');
        });
    }

    // Draw Agent
    if (world.agent) {
        drawAgent(world.agent.x, world.agent.y, world.agent.angle);
    }
}

/**
 * @param {number} x
 * @param {number} y
 * @param {number} angle
 */
function drawAgent(x, y, angle) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(angle);

    ctx.save();
    const visionRadius = 250; // Increased slightly
    const fov = 120 * (Math.PI / 180); 
    
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.arc(0, 0, visionRadius, -fov / 2, fov / 2);
    ctx.closePath();

    const gradient = ctx.createRadialGradient(0, 0, 0, 0, 0, visionRadius);
    gradient.addColorStop(0, 'rgba(255, 200, 50, 0.6)'); 
    gradient.addColorStop(0.5, 'rgba(255, 220, 100, 0.3)');
    gradient.addColorStop(1, 'rgba(255, 255, 100, 0)');
    
    ctx.fillStyle = gradient;
    ctx.fill();
    ctx.restore();

    // Tail
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(-10, 0);
    ctx.bezierCurveTo(-25, 5, -30, -10, -40, -5);
    ctx.strokeStyle = '#pink';
    ctx.lineWidth = 3;
    ctx.lineCap = 'round';
    ctx.strokeStyle = '#eca1a6';
    ctx.stroke();
    ctx.restore();

    // Body
    ctx.beginPath();
    ctx.ellipse(0, 0, 20, 14, 0, 0, Math.PI * 2); 
    ctx.fillStyle = '#64748b';
    ctx.fill();
    ctx.strokeStyle = '#475569';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Ears
    ctx.fillStyle = '#64748b';
    ctx.strokeStyle = '#475569';
    
    // Left Ear
    ctx.beginPath();
    ctx.arc(8, -12, 6, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    
    // Right Ear
    ctx.beginPath();
    ctx.arc(8, 12, 6, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();

    // Eyes
    ctx.fillStyle = 'black';
    ctx.beginPath();
    ctx.arc(12, -5, 2, 0, Math.PI * 2); // Left eye
    ctx.arc(12, 5, 2, 0, Math.PI * 2);  // Right eye
    ctx.fill();

    // Nose
    ctx.fillStyle = '#fda4af';
    ctx.beginPath();
    ctx.arc(18, 0, 3, 0, Math.PI*2);
    ctx.fill();
    
    // Whiskers
    ctx.strokeStyle = '#334155';
    ctx.lineWidth = 1;
    ctx.beginPath();
    
    // Left Whiskers
    ctx.moveTo(18, -2); ctx.lineTo(28, -8);
    ctx.moveTo(18, -2); ctx.lineTo(28, -5);
    
    // Right Whiskers
    ctx.moveTo(18, 2); ctx.lineTo(28, 8);
    ctx.moveTo(18, 2); ctx.lineTo(28, 5);
    ctx.stroke();

    ctx.restore();
}

function drawEntity(x, y, radius, color) {
    ctx.save();
    ctx.shadowBlur = 10;
    ctx.shadowColor = '#00ff41';
    
    ctx.beginPath();
    ctx.arc(x, y, 6, 0, Math.PI * 2); // Slightly smaller but glowing
    ctx.fillStyle = '#00ff41';
    ctx.fill();
    ctx.restore();
}

/**
 * @param {SimulationState} world
 */
function updateTelemetry(world) {
    if (world.agent) {
        elMouseX.innerText = String(Math.round(world.agent.x));
        elMouseY.innerText = String(Math.round(world.agent.y));
    }

    if (world.food) {
        elFoodCount.innerText = String(world.food.length);
    }

    if (world.simulationTimeMs !== undefined) {
        const seconds = world.simulationTimeMs / 1_000;
        elSimTime.innerText = seconds.toFixed(2) + 's';
    }
}

/**
 * @param {SimulationState} world
 */
function updateSnnTelemetry(world) {
    if (!world.snnDiagnostics) return;

    const diag = world.snnDiagnostics;
    elDopamine.innerText = diag.dopamineLevel.toFixed(4);
    elDopamineBase.innerText = diag.dopamineBaseLevel.toFixed(4);
    elMeanHz.innerText = diag.meanFiringRateHz.toFixed(2) + ' Hz';
    elSpikesLastStep.innerText = String(diag.totalSpikesInLastStep);
    elAvgWeight.innerText = diag.averageWeight.toFixed(2);
    elWeightRange.innerText = `${diag.minWeight.toFixed(2)} / ${diag.maxWeight.toFixed(2)}`;

}

/**
 * Adds one simulation step to the spike activity history. This is intentionally called
 * only from the SSE handler, never from requestAnimationFrame.
 *
 * @param {SimulationState} world
 */
function bufferSpikes(world) {
    const timeMs = Number(world.simulationTimeMs);
    if (!Number.isFinite(timeMs) || !world.snnDiagnostics) return;

    if (lastSpikeTimeMs !== null && timeMs < lastSpikeTimeMs) {
        resetSpikeHistory(false);
    }

    const diag = world.snnDiagnostics;
    const potentialsCount = Array.isArray(diag.neuronPotentials)
        ? diag.neuronPotentials.length
        : 0;
    const rawFiredIndices = Array.isArray(diag.firedNeuronIndices)
        ? diag.firedNeuronIndices
        : [];
    let highestFiredIndex = -1;
    for (const index of rawFiredIndices) {
        if (Number.isInteger(index) && index >= 0) {
            highestFiredIndex = Math.max(highestFiredIndex, index);
        }
    }

    const nextNeuronCount = potentialsCount > 0
        ? potentialsCount
        : Math.max(spikeActivityNeuronCount, highestFiredIndex + 1, DEFAULT_NEURON_COUNT);

    if (nextNeuronCount !== spikeActivityNeuronCount) {
        spikeActivityNeuronCount = nextNeuronCount;
        spikeBins.clear();
        resizeSpikeCanvas();
    }

    const binId = Math.floor(timeMs / SPIKE_ACTIVITY_BIN_MS);
    if (latestSpikeBin !== null && binId < latestSpikeBin) {
        resetSpikeHistory(false);
    }

    if (rawFiredIndices.length > 0) {
        let occupiedNeurons = spikeBins.get(binId);
        if (!occupiedNeurons) {
            occupiedNeurons = new Uint8Array(spikeActivityNeuronCount);
            spikeBins.set(binId, occupiedNeurons);
        }

        for (const index of rawFiredIndices) {
            if (Number.isInteger(index) && index >= 0 && index < spikeActivityNeuronCount) {
                occupiedNeurons[index] = 1;
            }
        }
    }

    lastSpikeTimeMs = timeMs;
    latestSpikeBin = binId;

    const oldestVisibleBin = binId - SPIKE_ACTIVITY_BIN_COUNT + 1;
    for (const existingBin of spikeBins.keys()) {
        if (existingBin >= oldestVisibleBin) break;
        spikeBins.delete(existingBin);
    }

    spikeActivityDirty = true;
}

/**
 * @param {boolean} resetNeuronCount
 */
function resetSpikeHistory(resetNeuronCount) {
    spikeBins.clear();
    lastSpikeTimeMs = null;
    latestSpikeBin = null;
    spikeActivityDirty = true;

    if (resetNeuronCount) {
        spikeActivityNeuronCount = DEFAULT_NEURON_COUNT;
        resizeSpikeCanvas();
    }
}

function resizeSpikeCanvas() {
    const desiredHeight = SPIKE_ACTIVITY_VERTICAL_PADDING + spikeActivityNeuronCount * 5;
    snnCanvas.height = Math.min(
        MAX_SPIKE_ACTIVITY_HEIGHT,
        Math.max(MIN_SPIKE_ACTIVITY_HEIGHT, desiredHeight)
    );
    spikeActivityDirty = true;
}

function drawSpikeActivity() {
    const width = snnCanvas.width;
    const height = snnCanvas.height;
    const plot = {
        left: 50,
        top: 12,
        right: width - 8,
        bottom: height - 30
    };
    const plotWidth = plot.right - plot.left;
    const plotHeight = plot.bottom - plot.top;
    snnCtx.clearRect(0, 0, width, height);
    snnCtx.fillStyle = '#0b0f19';
    snnCtx.fillRect(0, 0, width, height);
    snnCtx.font = "9px 'Roboto Mono', monospace";
    snnCtx.lineWidth = 1;

    // Time grid: newest samples are at x = plot.right.
    snnCtx.textAlign = 'center';
    snnCtx.textBaseline = 'top';
    for (let tick = 0; tick <= 4; tick++) {
        const x = plot.left + (tick / 4) * plotWidth;
        const ageMs = SPIKE_WINDOW_MS - (tick / 4) * SPIKE_WINDOW_MS;

        snnCtx.strokeStyle = tick === 4
            ? 'rgba(0, 255, 255, 0.55)'
            : 'rgba(0, 255, 255, 0.12)';
        snnCtx.beginPath();
        snnCtx.moveTo(Math.round(x) + 0.5, plot.top);
        snnCtx.lineTo(Math.round(x) + 0.5, plot.bottom);
        snnCtx.stroke();

        snnCtx.fillStyle = '#94a3b8';
        snnCtx.fillText(ageMs === 0 ? '0' : `-${Math.round(ageMs)}`, x, plot.bottom + 5);
    }

    // A small set of horizontal guides keeps dense spike activity readable.
    const labelStep = Math.max(1, Math.ceil(spikeActivityNeuronCount / 6));
    snnCtx.textAlign = 'right';
    snnCtx.textBaseline = 'middle';
    for (let index = 0; index < spikeActivityNeuronCount; index += labelStep) {
        const y = neuronY(index, plot.top, plotHeight);
        snnCtx.strokeStyle = 'rgba(0, 255, 65, 0.10)';
        snnCtx.beginPath();
        snnCtx.moveTo(plot.left, Math.round(y) + 0.5);
        snnCtx.lineTo(plot.right, Math.round(y) + 0.5);
        snnCtx.stroke();

        snnCtx.fillStyle = '#94a3b8';
        snnCtx.fillText(`N${index}`, plot.left - 5, y);
    }

    const lastIndex = spikeActivityNeuronCount - 1;
    if (lastIndex >= 0 && lastIndex % labelStep !== 0) {
        const y = neuronY(lastIndex, plot.top, plotHeight);
        snnCtx.fillStyle = '#94a3b8';
        snnCtx.fillText(`N${lastIndex}`, plot.left - 5, y);
    }

    snnCtx.save();
    snnCtx.translate(8, plot.top + plotHeight / 2);
    snnCtx.rotate(-Math.PI / 2);
    snnCtx.fillStyle = '#94a3b8';
    snnCtx.textAlign = 'center';
    snnCtx.textBaseline = 'top';
    snnCtx.fillText('NEURON', 0, 0);
    snnCtx.restore();

    snnCtx.save();
    snnCtx.beginPath();
    snnCtx.rect(plot.left, plot.top, plotWidth, plotHeight);
    snnCtx.clip();
    drawSpikeActivityBitmap(plot.left, plot.top, plotWidth, plotHeight);
    snnCtx.restore();

    snnCtx.fillStyle = '#94a3b8';
    snnCtx.textAlign = 'right';
    snnCtx.textBaseline = 'bottom';
    snnCtx.fillText('TIME [ms]', plot.right, height - 2);
    spikeActivityDirty = false;
}

function drawSpikeActivityBitmap(x, y, width, height) {
    const bitmapHeight = Math.max(1, spikeActivityNeuronCount);
    if (
        spikeActivityBitmapCanvas.width !== SPIKE_ACTIVITY_BIN_COUNT ||
        spikeActivityBitmapCanvas.height !== bitmapHeight ||
        spikeActivityBitmap === null
    ) {
        spikeActivityBitmapCanvas.width = SPIKE_ACTIVITY_BIN_COUNT;
        spikeActivityBitmapCanvas.height = bitmapHeight;
        spikeActivityBitmap = spikeActivityBitmapCtx.createImageData(
            SPIKE_ACTIVITY_BIN_COUNT,
            bitmapHeight
        );
    }

    const pixels = spikeActivityBitmap.data;
    pixels.fill(0);

    if (latestSpikeBin !== null) {
        for (const [binId, occupiedNeurons] of spikeBins) {
            const ageInBins = latestSpikeBin - binId;
            const column = SPIKE_ACTIVITY_BIN_COUNT - 1 - ageInBins;
            if (column < 0 || column >= SPIKE_ACTIVITY_BIN_COUNT) continue;

            for (let neuronIndex = 0; neuronIndex < occupiedNeurons.length; neuronIndex++) {
                if (occupiedNeurons[neuronIndex] === 0) continue;

                const pixelIndex = (neuronIndex * SPIKE_ACTIVITY_BIN_COUNT + column) * 4;
                pixels[pixelIndex] = 0;
                pixels[pixelIndex + 1] = 255;
                pixels[pixelIndex + 2] = 65;
                pixels[pixelIndex + 3] = 255;
            }
        }
    }

    spikeActivityBitmapCtx.putImageData(spikeActivityBitmap, 0, 0);
    snnCtx.imageSmoothingEnabled = false;
    snnCtx.drawImage(spikeActivityBitmapCanvas, x, y, width, height);
}

function neuronY(index, plotTop, plotHeight) {
    return plotTop + ((index + 0.5) / Math.max(1, spikeActivityNeuronCount)) * plotHeight;
}

resizeSpikeCanvas();
toggleBtn.addEventListener('click', toggleSimulation);
