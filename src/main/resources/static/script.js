const canvas = document.getElementById('simCanvas');
const ctx = canvas.getContext('2d');
const toggleBtn = document.getElementById('toggleBtn');
const elMouseX = document.getElementById('mouseX');
const elMouseY = document.getElementById('mouseY');
const elFoodCount = document.getElementById('foodCount');

const API_URL = '/api/state';

let animationFrameId = null;
let isRunning = false;

/**
 * @typedef {Object} World
 * @property {Object} agent
 * @property {number} agent.x
 * @property {number} agent.y
 * @property {number} agent.angle
 * @property {Array<{x: number, y: number}>} food
 */

function toggleSimulation() {
    if (isRunning) {
        stopSimulation();
    } else {
        startSimulation();
    }
}

function startSimulation() {
    if (isRunning) return;
    isRunning = true;
    toggleBtn.textContent = "Stop Simulation";
    toggleBtn.classList.add('stop');

    gameLoop().catch(console.error);
}

function stopSimulation() {
    isRunning = false;
    toggleBtn.textContent = "Start Simulation";
    toggleBtn.classList.remove('stop');

    if (animationFrameId) {
        cancelAnimationFrame(animationFrameId);
        animationFrameId = null;
    }
}

async function gameLoop() {
    if (!isRunning) return;

    try {
        const response = await fetch(API_URL);

        if (response.ok) {
            /** @type {World} */
            const worldData = await response.json();
            render(worldData);
            updateTelemetry(worldData);
        }
    } catch (error) {
        console.error("Simulation fetch error:", error);
    }

    if (isRunning) {
        animationFrameId = requestAnimationFrame(gameLoop);
    }
}

/**
 * @param {World} world
 */
function render(world) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (world.food && Array.isArray(world.food)) {
        world.food.forEach(f => {
            drawEntity(f.x, f.y, 8, '#22c55e');
        });
    }

    if (world.agent) {
        drawAgent(world.agent.x, world.agent.y, world.agent.angle);
    }
}

function drawAgent(x, y, angle) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(angle);

    // Agent Body
    ctx.beginPath();
    ctx.arc(0, 0, 15, 0, Math.PI * 2);
    ctx.fillStyle = '#2563eb'; // Blue
    ctx.fill();

    // Agent Border
    ctx.strokeStyle = '#1e40af';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Direction Indicator
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
 * @param {World} world
 */
function updateTelemetry(world) {
    if (world.agent) {
        elMouseX.innerText = String(Math.round(world.agent.x));
        elMouseY.innerText = String(Math.round(world.agent.y));
    }
    if (world.food && Array.isArray(world.food)) {
        elFoodCount.innerText = String(world.food.length);
    }
}

toggleBtn.addEventListener('click', toggleSimulation);