const canvas = document.querySelector("#drawing-canvas");
const context = canvas.getContext("2d", { willReadFrequently: true });
const frame = document.querySelector(".canvas-frame");
const colorPicker = document.querySelector("#color-picker");
const colorValue = document.querySelector("#color-value");
const sizeInput = document.querySelector("#brush-size");
const sizeValue = document.querySelector("#size-value");
const undoButton = document.querySelector("#undo-button");
const redoButton = document.querySelector("#redo-button");
const liveMessage = document.querySelector("#live-message");

const history = [];
const redoHistory = [];
const maxHistory = 25;
let activeTool = "brush";
let isDrawing = false;
let startPoint = null;
let previousPoint = null;
let previewSnapshot = null;
let resizeTimer;
let installPrompt;

function snapshot() {
  return context.getImageData(0, 0, canvas.width, canvas.height);
}

function updateHistoryButtons() {
  undoButton.disabled = history.length === 0;
  redoButton.disabled = redoHistory.length === 0;
}

function rememberState() {
  history.push(snapshot());
  if (history.length > maxHistory) history.shift();
  redoHistory.length = 0;
  updateHistoryButtons();
}

function restoreState(state) {
  context.putImageData(state, 0, 0);
}

function setCanvasSize() {
  const width = Math.max(1, Math.round(frame.clientWidth));
  const height = Math.max(1, Math.round(frame.clientHeight));
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2);
  const oldCanvas = document.createElement("canvas");
  oldCanvas.width = canvas.width;
  oldCanvas.height = canvas.height;

  if (canvas.width && canvas.height) {
    oldCanvas.getContext("2d").drawImage(canvas, 0, 0);
  }

  canvas.width = Math.round(width * pixelRatio);
  canvas.height = Math.round(height * pixelRatio);
  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, width, height);

  if (oldCanvas.width && oldCanvas.height) {
    context.setTransform(1, 0, 0, 1, 0, 0);
    context.drawImage(oldCanvas, 0, 0, canvas.width, canvas.height);
    context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0);
  }

  history.length = 0;
  redoHistory.length = 0;
  updateHistoryButtons();
}

function pointFromEvent(event) {
  const bounds = canvas.getBoundingClientRect();
  return { x: event.clientX - bounds.left, y: event.clientY - bounds.top };
}

function drawLine(from, to) {
  context.beginPath();
  context.moveTo(from.x, from.y);
  context.lineTo(to.x, to.y);
  context.stroke();
}

function drawShape(point) {
  context.beginPath();
  if (activeTool === "line") {
    context.moveTo(startPoint.x, startPoint.y);
    context.lineTo(point.x, point.y);
  } else if (activeTool === "rectangle") {
    context.rect(startPoint.x, startPoint.y, point.x - startPoint.x, point.y - startPoint.y);
  } else {
    const centerX = (startPoint.x + point.x) / 2;
    const centerY = (startPoint.y + point.y) / 2;
    const radiusX = Math.abs(point.x - startPoint.x) / 2;
    const radiusY = Math.abs(point.y - startPoint.y) / 2;
    context.ellipse(centerX, centerY, Math.max(radiusX, 0.5), Math.max(radiusY, 0.5), 0, 0, Math.PI * 2);
  }
  context.stroke();
}

function configureStroke() {
  context.lineWidth = Number(sizeInput.value);
  context.lineCap = "round";
  context.lineJoin = "round";
  context.strokeStyle = activeTool === "eraser" ? "#ffffff" : colorPicker.value;
}

canvas.addEventListener("pointerdown", (event) => {
  if (event.button !== 0 && event.pointerType === "mouse") return;
  event.preventDefault();
  canvas.setPointerCapture(event.pointerId);
  isDrawing = true;
  startPoint = pointFromEvent(event);
  previousPoint = startPoint;
  previewSnapshot = snapshot();
  rememberState();
  configureStroke();
  if (activeTool === "brush" || activeTool === "eraser") {
    drawLine(startPoint, { x: startPoint.x + 0.01, y: startPoint.y + 0.01 });
  }
});

canvas.addEventListener("pointermove", (event) => {
  if (!isDrawing) return;
  const point = pointFromEvent(event);
  configureStroke();
  if (activeTool === "brush" || activeTool === "eraser") {
    drawLine(previousPoint, point);
    previousPoint = point;
    return;
  }
  restoreState(previewSnapshot);
  drawShape(point);
});

function finishDrawing(event) {
  if (!isDrawing) return;
  if (event && canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
  isDrawing = false;
  startPoint = null;
  previousPoint = null;
  previewSnapshot = null;
}

canvas.addEventListener("pointerup", finishDrawing);
canvas.addEventListener("pointercancel", finishDrawing);
canvas.addEventListener("lostpointercapture", finishDrawing);

document.querySelectorAll(".tool-button").forEach((button) => {
  button.addEventListener("click", () => {
    activeTool = button.dataset.tool;
    document.querySelectorAll(".tool-button").forEach((toolButton) => {
      const selected = toolButton === button;
      toolButton.classList.toggle("is-active", selected);
      toolButton.setAttribute("aria-pressed", String(selected));
    });
    canvas.style.cursor = activeTool === "eraser" ? "cell" : "crosshair";
  });
});

function selectColor(color) {
  colorPicker.value = color;
  colorValue.textContent = color.toUpperCase();
  document.querySelectorAll(".swatch").forEach((swatch) => {
    swatch.classList.toggle("is-selected", swatch.dataset.color.toLowerCase() === color.toLowerCase());
  });
}

colorPicker.addEventListener("input", () => selectColor(colorPicker.value));
document.querySelectorAll(".swatch").forEach((swatch) => {
  swatch.addEventListener("click", () => selectColor(swatch.dataset.color));
});

sizeInput.addEventListener("input", () => {
  sizeValue.value = `${sizeInput.value} px`;
  sizeValue.textContent = `${sizeInput.value} px`;
});

undoButton.addEventListener("click", () => {
  if (history.length === 0) return;
  redoHistory.push(snapshot());
  restoreState(history.pop());
  updateHistoryButtons();
});

redoButton.addEventListener("click", () => {
  if (redoHistory.length === 0) return;
  history.push(snapshot());
  restoreState(redoHistory.pop());
  updateHistoryButtons();
});

document.querySelector("#clear-button").addEventListener("click", () => {
  if (!window.confirm("Deseja apagar todo o desenho?")) return;
  rememberState();
  context.save();
  context.setTransform(1, 0, 0, 1, 0, 0);
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.restore();
  liveMessage.textContent = "Desenho apagado. Você pode desfazer essa ação.";
});

document.querySelector("#save-button").addEventListener("click", () => {
  const link = document.createElement("a");
  link.download = `meu-desenho-${new Date().toISOString().slice(0, 10)}.png`;
  link.href = canvas.toDataURL("image/png");
  link.click();
  liveMessage.textContent = "Desenho salvo em PNG.";
});

document.addEventListener("keydown", (event) => {
  if (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== "z") return;
  event.preventDefault();
  if (event.shiftKey) redoButton.click();
  else undoButton.click();
});

window.addEventListener("resize", () => {
  window.clearTimeout(resizeTimer);
  resizeTimer = window.setTimeout(setCanvasSize, 120);
});

window.addEventListener("beforeinstallprompt", (event) => {
  event.preventDefault();
  installPrompt = event;
  document.querySelector("#install-button").hidden = false;
});

document.querySelector("#install-button").addEventListener("click", async () => {
  if (!installPrompt) return;
  await installPrompt.prompt();
  installPrompt = null;
  document.querySelector("#install-button").hidden = true;
});

setCanvasSize();

if ("serviceWorker" in navigator && window.location.protocol !== "file:") {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("./sw.js").catch((error) => {
      console.error("Não foi possível ativar o modo offline:", error);
    });
  });
}
