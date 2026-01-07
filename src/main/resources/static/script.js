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
 */

const canvas = document.getElementById('simCanvas');
const ctx = canvas.getContext('2d');
const toggleBtn = document.getElementById('toggleBtn');
const elMouseX = document.getElementById('mouseX');
const elMouseY = document.getElementById('mouseY');
const elFoodCount = document.getElementById('foodCount');

const API_URL = '/api/state';

let animationFrameId = null;
let isRunning = false;

function toggleSimulation() {
    if (isRunning) {
        stopSimulation();
    } else {
        startSimulation();
    }
}

function startSimulation() {
    if (isRunning) return;

    fetch('/api/start').then(() => {
        isRunning = true;
        toggleBtn.textContent = "Stop Simulation";
        toggleBtn.classList.add('stop');
        gameLoop().catch(console.error);
    }).catch(err => console.error("Failed to start:", err));
}

function stopSimulation() {
    fetch('/api/stop').then(() => {
        isRunning = false;
        toggleBtn.textContent = "Start Simulation";
        toggleBtn.classList.remove('stop');

        if (animationFrameId) {
            cancelAnimationFrame(animationFrameId);
            animationFrameId = null;
        }
    }).catch(err => console.error("Failed to stop:", err));
}

async function gameLoop() {
    if (!isRunning) return;

    try {
        const response = await fetch(API_URL);

        if (response.ok) {
            /** @type {SimulationState} */
            const data = await response.json();
            render(data);
            updateTelemetry(data);
        }
    } catch (error) {
        console.error("Simulation fetch error:", error);
    }

    if (isRunning) {
        animationFrameId = requestAnimationFrame(gameLoop);
    }
}

/**
 * @param {SimulationState} world
 */
function render(world) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Draw Food
    if (world.food) {
        world.food.forEach(f => {
            drawEntity(f.x, f.y, 8, '#22c55e');
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
    ctx.rotate(angle); // Assumes angle is in Radians from backend

    // Agent Body
    ctx.beginPath();
    ctx.arc(0, 0, 15, 0, Math.PI * 2);
    ctx.fillStyle = '#2563eb'; // Blue
    ctx.fill();

    // Agent Border
    ctx.strokeStyle = '#1e40af';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Direction Indicator (Nose)
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.lineTo(20, 0);
    ctx.strokeStyle = 'white';
    ctx.lineWidth = 2;
    ctx.stroke();

    ctx.restore();
}

function drawEntity(x, y, radius, color) {
    ctx.beginPath();
    ctx.arc(x, y, radius, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();
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
}

toggleBtn.addEventListener('click', toggleSimulation);