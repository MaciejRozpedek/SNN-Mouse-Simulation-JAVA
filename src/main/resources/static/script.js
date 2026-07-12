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

reloadBtn.addEventListener('click', () => {
    stopSimulation()
    fetch('/api/reload', { method: 'POST' })
        .then(() => {
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
let showNetworkMap = false;

toggleNetBtn.addEventListener('click', () => {
    showNetworkMap = !showNetworkMap;
    snnCanvas.style.display = showNetworkMap ? 'block' : 'none';
    toggleNetBtn.textContent = showNetworkMap ? 'Hide Map' : 'Show Map';
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

function renderLoop() {
    if (!isRunning) return;

    if (latestWorldState) {
        render(latestWorldState);
        updateTelemetry(latestWorldState);
        updateSnnTelemetry(latestWorldState);
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

    if (showNetworkMap && diag.neuronPotentials) {
        drawNetworkActivity(diag.neuronPotentials, diag.firedNeuronIndices);
    }
}

/**
 * @param {number[]} potentials
 * @param {number[]} firedIndices
 */
function drawNetworkActivity(potentials, firedIndices) {
    snnCtx.clearRect(0, 0, snnCanvas.width, snnCanvas.height);

    const count = potentials.length;
    if (count === 0) return;

    const cols = Math.ceil(Math.sqrt(count));
    const rows = Math.ceil(count / cols);
    const cellWidth = snnCanvas.width / cols;
    const cellHeight = snnCanvas.height / rows;
    const radius = Math.max(1, Math.min(cellWidth, cellHeight) * 0.38);
    const firedSet = new Set(firedIndices || []);

    for (let i = 0; i < count; i++) {
        const col = i % cols;
        const row = Math.floor(i / cols);
        const x = col * cellWidth + cellWidth / 2;
        const y = row * cellHeight + cellHeight / 2;
        const normalizedV = Math.min(Math.max((potentials[i] + 70) / 100, 0), 1);

        snnCtx.beginPath();
        snnCtx.arc(x, y, radius, 0, Math.PI * 2);

        if (firedSet.has(i)) {
            snnCtx.fillStyle = '#ffffff';
            snnCtx.shadowBlur = 8;
            snnCtx.shadowColor = '#00ff41';
        } else {
            snnCtx.fillStyle = `rgba(0, 255, 65, ${Math.max(0.12, normalizedV)})`;
            snnCtx.shadowBlur = 0;
        }

        snnCtx.fill();
    }

    snnCtx.shadowBlur = 0;
}

toggleBtn.addEventListener('click', toggleSimulation);
