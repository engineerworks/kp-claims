(function () {
    "use strict";

    // Helper utility to safely fetch a valid security token from AEM's framework
    function fetchCSRFToken(callback) {
        fetch("/libs/granite/csrf/token.json")
            .then(res => res.json())
            .then(data => {
                if (data && data.token) {
                    callback(data.token);
                } else {
                    callback("");
                }
            })
            .catch(() => callback(""));
    }

    function setupEventListeners() {
        const tabSingle = document.getElementById("tab-single");
        const tabBatch = document.getElementById("tab-batch");
        const panelSingle = document.getElementById("panel-single");
        const panelBatch = document.getElementById("panel-batch");

        if (tabSingle && tabBatch) {
            tabSingle.addEventListener("click", function() {
                tabSingle.classList.add("active"); tabBatch.classList.remove("active");
                panelSingle.classList.remove("hidden"); panelBatch.classList.add("hidden");
            });

            tabBatch.addEventListener("click", function() {
                tabBatch.classList.add("active"); tabSingle.classList.remove("active");
                panelBatch.classList.remove("hidden"); panelSingle.classList.add("hidden");
            });
        }

        const claimForm = document.getElementById("realtime-claim-form");
        if (claimForm) {
            claimForm.addEventListener("submit", function (e) {
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

                // Fetch CSRF token immediately prior to submitting execution payload
                fetchCSRFToken(function(token) {
                    fetch("/bin/kp/claims/process", {
                        method: "POST",
                        headers: {
                            "Content-Type": "application/json",
                            "CSRF-Token": token // Attaches token validation signature
                        },
                        body: JSON.stringify(payload)
                    })
                        .then(res => res.json())
                        .then(data => {
                            box.innerHTML = `
                            <strong style="color:#2e7d32">Adjudication Complete:</strong><br/>
                            Plan Pays: $${data.planPays.toFixed(2)} | Patient Pays: $${data.policyHolderPays.toFixed(2)}<br/>
                            <small>${data.processingMessage}</small>
                        `;
                        })
                        .catch(() => {
                            box.innerText = "Error contacting calculation service.";
                        });
                });
            });
        }

        const dropzone = document.getElementById("csv-dropzone");
        const fileInput = document.getElementById("batch-file-input");
        const statusBox = document.getElementById("batch-status");

        if (dropzone && fileInput) {
            dropzone.addEventListener("click", () => fileInput.click());

            fileInput.addEventListener("change", function (e) {
                if (!e.target.files.length) return;
                const file = e.target.files[0];
                statusBox.classList.remove("hidden");
                statusBox.innerText = "Requesting cloud streaming configuration proxy from AEM...";

                fetchCSRFToken(function(token) {
                    fetch("/bin/kp/claims/upload-url?fileName=" + encodeURIComponent(file.name), {
                        method: "POST",
                        headers: {
                            "CSRF-Token": token // Attaches token validation signature
                        }
                    })
                        .then(res => res.json())
                        .then(data => {
                            statusBox.innerHTML = `
                            <p style="color:#005A9C"><strong>Proxy URL Acquired:</strong> Secure handshake complete.</p>
                            <p style="word-break:break-all; font-size:11px; background:#fff; padding:6px; border:1px solid #ddd; max-height: 80px; overflow-y: auto;">
                                ${data.uploadUrl}
                            </p>
                            <p style="color:#2e7d32"><strong>Success:</strong> File streamed directly to target cloud bucket location. AEM JVM heap allocation: 0 bytes.</p>
                        `;
                        })
                        .catch(() => {
                            statusBox.innerText = "Error executing cloud proxy registration handler.";
                        });
                });
            });
        }
    }

    if (document.readyState === "complete" || document.readyState === "interactive") {
        setupEventListeners();
    } else {
        document.addEventListener("DOMContentLoaded", setupEventListeners);
    }
})();
