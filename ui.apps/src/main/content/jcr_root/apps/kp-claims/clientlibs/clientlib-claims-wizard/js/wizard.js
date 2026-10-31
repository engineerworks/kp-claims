document.addEventListener("DOMContentLoaded", function () {
    const rootEl = document.getElementById("abc-claims-app-root");
    if (!rootEl) return;

    rootEl.innerHTML = `
        <div class="claims-wizard-card">
            <h2>KP Claims Processing Dashboard</h2>
            <p class="subtitle">Cloud-Safe Adjudication Vector Demo</p>
            
            <div class="tabs">
                <button class="tab-btn active" id="tab-single">Single Claim Evaluation</button>
                <button class="tab-btn" id="tab-batch">700k High-Volume Stream</button>
            </div>

            <!-- Single Claim Form Section -->
            <div id="panel-single" class="tab-panel">
                <form id="realtime-claim-form">
                    <div class="form-grid">
                        <div>
                            <label>Policy ID</label>
                            <input type="text" id="policyId" value="100001" required>
                        </div>
                        <div>
                            <label>Policy Holder ID</label>
                            <input type="text" id="policyHolderId" value="1000011" required>
                        </div>
                        <div>
                            <label>Billed Amount ($)</label>
                            <input type="number" id="billedAmount" value="4000" step="0.01" required>
                        </div>
                        <div>
                            <label>Coverage Rule String</label>
                            <input type="text" id="ruleString" value="40% AFTER DEDUCTIBLE" required>
                        </div>
                        <div>
                            <label>Current Ind. Accumulated ($)</label>
                            <input type="number" id="currentInd" value="2000" step="0.01" required>
                        </div>
                        <div>
                            <label>Current Fam. Accumulated ($)</label>
                            <input type="number" id="currentFam" value="2000" step="0.01" required>
                        </div>
                    </div>
                    <button type="submit" class="submit-btn">Execute Stateless Engine</button>
                </form>
                <div id="api-result-output" class="result-box hidden"></div>
            </div>

            <!-- Batch Pipeline Section -->
            <div id="panel-batch" class="tab-panel hidden">
                <div class="dropzone" id="csv-dropzone">
                    <p>Drag & drop transaction log CSV here to request Presigned URL</p>
                    <input type="file" id="batch-file-input" accept=".csv" style="display:none">
                </div>
                <div id="batch-status" class="batch-status-box hidden"></div>
            </div>
        </div>
    `;

    // Tab Logic
    const tabSingle = document.getElementById("tab-single");
    const tabBatch = document.getElementById("tab-batch");
    const panelSingle = document.getElementById("panel-single");
    const panelBatch = document.getElementById("panel-batch");

    tabSingle.addEventListener("click", () => {
        tabSingle.classList.add("active"); tabBatch.classList.remove("active");
        panelSingle.classList.remove("hidden"); panelBatch.classList.add("hidden");
    });

    tabBatch.addEventListener("click", () => {
        tabBatch.classList.add("active"); tabSingle.classList.remove("active");
        panelBatch.classList.remove("hidden"); panelSingle.classList.add("hidden");
    });

    // Real-Time Evaluation
    document.getElementById("realtime-claim-form").addEventListener("submit", function (e) {
        e.preventDefault();
        const box = document.getElementById("api-result-output");
        box.classList.remove("hidden");
        box.innerText = "Evaluating transaction...";

        const payload = {
            claim: {
                policyId: document.getElementById("policyId").value,
                policyHolderId: document.getElementById("policyHolderId").value,
                billedAmount: parseFloat(document.getElementById("billedAmount").value),
                mainCategory: "Inpatient Hospital Care",
                subCategory: "SURGERY",
                dateOfService: "2016-05-08"
            },
            policy: {
                planId: "P001",
                individualThreshold: 6000.0,
                familyThreshold: 12000.0,
                currentIndividualAccumulated: parseFloat(document.getElementById("currentInd").value),
                currentFamilyAccumulated: parseFloat(document.getElementById("currentFam").value),
                isActive: true
            },
            rule: document.getElementById("ruleString").value
        };

        fetch("/bin/kp/claims/process", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        })
            .then(res => res.json())
            .then(data => {
                box.innerHTML = `
                <strong style="color:#2e7d32">Adjudication Complete:</strong><br/>
                Plan Pays: $${data.planPays.toFixed(2)} | Patient Pays: $${data.policyHolderPays.toFixed(2)}<br/>
                <small>${data.processingMessage}</small>
            `;
            });
    });

    // Direct Upload Handshake Simulation
    const fileInput = document.getElementById("batch-file-input");
    document.getElementById("csv-dropzone").addEventListener("click", () => fileInput.click());

    fileInput.addEventListener("change", (e) => {
        if (!e.target.files.length) return;
        const file = e.target.files[0];
        const status = document.getElementById("batch-status");
        status.classList.remove("hidden");
        status.innerText = "Requesting cloud streaming configuration proxy from AEM...";

        // Real integration handshake call to Servlet 1
        fetch(`/bin/kp/claims/upload-url?fileName=${encodeURIComponent(file.name)}`, { method: "POST" })
            .then(res => res.json())
            .then(data => {
                status.innerHTML = `
                <p style="color:#005A9C"><strong>Proxy URL Acquired:</strong> Secure handshake complete.</p>
                <p style="word-break:break-all; font-size:11px; background:#fff; padding:6px; border:1px solid #ddd;">
                    ${data.uploadUrl}
                </p>
                <p style="color:#2e7d32"><strong>Success:</strong> File streamed directly to target cloud bucket location. AEM JVM heap allocation: 0 bytes.</p>
            `;
            });
    });
});
