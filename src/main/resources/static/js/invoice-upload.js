const form = document.querySelector("#invoice-form");
const fileInput = document.querySelector("#invoice-file");
const dropArea = document.querySelector("#drop-area");
const fileName = document.querySelector("#file-name");
const submitButton = document.querySelector("#submit-invoice");
const message = document.querySelector("#form-message");
const analysisPanel = document.querySelector("#analysis-panel");
const ruleList = document.querySelector("#rule-list");
const progressFill = document.querySelector("#progress-fill");
const progressTrack = document.querySelector("#progress-track");
const progressValue = document.querySelector("#progress-value");
const analysisTitle = document.querySelector("#analysis-title");
const analysisResult = document.querySelector("#analysis-result");
const discountValue = document.querySelector("#discount-value");
const discountLabel = document.querySelector("#discount-label");
const discountDetails = document.querySelector("#discount-details");
const benefitMessage = document.querySelector("#benefit-message");

fileInput.addEventListener("change", () => showSelectedFile(fileInput.files[0]));

["dragenter", "dragover"].forEach((eventName) => {
    dropArea.addEventListener(eventName, (event) => {
        event.preventDefault();
        dropArea.classList.add("is-dragging");
    });
});

["dragleave", "drop"].forEach((eventName) => {
    dropArea.addEventListener(eventName, (event) => {
        event.preventDefault();
        dropArea.classList.remove("is-dragging");
    });
});

dropArea.addEventListener("drop", (event) => {
    const [file] = event.dataTransfer.files;
    if (!file) return;
    const transfer = new DataTransfer();
    transfer.items.add(file);
    fileInput.files = transfer.files;
    showSelectedFile(file);
});

form.addEventListener("submit", async (event) => {
    event.preventDefault();
    message.textContent = "";
    analysisPanel.hidden = true;
    analysisResult.hidden = true;
    resetBenefitSummary();

    const file = fileInput.files[0];
    if (!file) {
        message.textContent = "Selecione uma imagem JPG ou PNG.";
        return;
    }
    if (!["image/jpeg", "image/png"].includes(file.type)) {
        message.textContent = "Formato não aceito. Envie uma imagem JPG ou PNG.";
        return;
    }
    if (file.size > 10 * 1024 * 1024) {
        message.textContent = "O arquivo deve ter no máximo 10 MB.";
        return;
    }

    submitButton.disabled = true;
    submitButton.textContent = "Analisando...";
    message.textContent = "Enviando a imagem para leitura. Isso pode levar alguns instantes.";

    try {
        const response = await fetch("/api/notas", {
            method: "POST",
            body: new FormData(form)
        });
        const result = await response.json();
        if (!response.ok) {
            throw new Error(result.erro || "Não foi possível analisar a nota.");
        }
        message.textContent = "";
        await showAnalysis(result);
    } catch (error) {
        message.textContent = error.message || "Falha de comunicação ao analisar a nota.";
    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "Analisar nota fiscal";
    }
});

function showSelectedFile(file) {
    if (file) fileName.textContent = file.name;
}

async function showAnalysis(result) {
    analysisPanel.hidden = false;
    ruleList.replaceChildren();
    progressFill.style.width = "0%";
    progressTrack.setAttribute("aria-valuenow", "0");
    progressValue.textContent = "0%";
    analysisTitle.textContent = "Resultado das regras";

    for (let index = 0; index < result.regras.length; index += 1) {
        const rule = result.regras[index];
        const item = document.createElement("li");
        item.className = `rule-item rule-${rule.estado.toLowerCase()}`;

        const status = document.createElement("span");
        status.className = "rule-status";
        status.setAttribute("aria-hidden", "true");
        status.textContent = statusIcon(rule.estado);

        const content = document.createElement("span");
        content.className = "rule-content";
        const title = document.createElement("strong");
        title.textContent = `${rule.id} · ${rule.titulo}`;
        const detail = document.createElement("small");
        detail.textContent = rule.detalhe;
        content.append(title, detail);
        item.append(status, content);
        ruleList.append(item);

        const percentage = Math.round(((index + 1) / result.regras.length) * 100);
        progressFill.style.width = `${percentage}%`;
        progressTrack.setAttribute("aria-valuenow", String(percentage));
        progressValue.textContent = `${percentage}%`;
        await new Promise((resolve) => window.setTimeout(resolve, 160));
    }

    updateBenefitSummary(result);
    const hasAmount = result.valorNota !== null && result.descontoEstimado !== null;
    const amountNotice = hasAmount ? "" : "Não foi possível identificar o valor total da nota. ";
    analysisResult.className = result.beneficioLiberado ? "analysis-result result-approved" : "analysis-result result-pending";
    analysisResult.textContent = result.beneficioLiberado
        ? "Benefício liberado após a validação."
        : `${amountNotice}Benefício não liberado: há regras pendentes ou reprovadas. O valor exibido é apenas uma estimativa até a validação oficial.`;
    if (result.aviso) analysisResult.textContent += ` ${result.aviso}`;
    analysisResult.hidden = false;
}

function resetBenefitSummary() {
    discountValue.textContent = "5%";
    discountValue.classList.remove("discount-value-amount");
    discountLabel.textContent = "de volta para você";
    discountDetails.textContent = "Envie uma nota para calcular o valor";
    benefitMessage.textContent = "Obrigado por participar do Ciclo Verde! Continue ajudando a cuidar do meio ambiente e inspirando um futuro mais sustentável.";
}

function updateBenefitSummary(result) {
    const hasAmount = result.valorNota !== null && result.descontoEstimado !== null;
    if (!hasAmount) {
        resetBenefitSummary();
        return;
    }

    discountValue.textContent = formatCurrency(result.descontoEstimado);
    discountValue.classList.add("discount-value-amount");
    discountLabel.textContent = result.beneficioLiberado ? "Desconto aprovado" : "Desconto estimado";
    discountDetails.textContent = `5% da nota de ${formatCurrency(result.valorNota)}`;
    const formattedDiscount = formatCurrency(result.descontoEstimado);
    benefitMessage.textContent = result.beneficioLiberado
        ? `Você receberá um desconto de ${formattedDiscount}! Obrigado por participar!`
        : `Você poderá receber um desconto estimado de ${formattedDiscount} após a validação. Obrigado por participar! Continue ajudando a cuidar do meio ambiente.`;
}

function statusIcon(status) {
    if (status === "VALIDA") return "✓";
    if (status === "REPROVADA") return "×";
    if (status === "NAO_APLICAVEL") return "—";
    return "…";
}

function formatCurrency(value) {
    return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(value);
}
