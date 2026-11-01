(function () {
    "use strict";

    function fetchCSRFToken() {
        return fetch("/libs/granite/csrf/token.json", {
            credentials: "same-origin"
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error(
                        "Unable to retrieve the AEM CSRF token."
                    );
                }

                return response.json();
            })
            .then(function (data) {
                if (!data || !data.token) {
                    throw new Error(
                        "AEM did not return a CSRF token."
                    );
                }

                return data.token;
            });
    }

    function setActivePanel(
        selectedTab,
        selectedPanel,
        otherTab,
        otherPanel
    ) {
        selectedTab.classList.add("active");
        selectedPanel.classList.remove("hidden");

        otherTab.classList.remove("active");
        otherPanel.classList.add("hidden");
    }

    function setupTabs() {
        var tabSingle =
            document.getElementById("tab-single");

        var tabBatch =
            document.getElementById("tab-batch");

        var panelSingle =
            document.getElementById("panel-single");

        var panelBatch =
            document.getElementById("panel-batch");

        if (!tabSingle
            || !tabBatch
            || !panelSingle
            || !panelBatch) {

            return;
        }

        tabSingle.addEventListener(
            "click",
            function () {
                setActivePanel(
                    tabSingle,
                    panelSingle,
                    tabBatch,
                    panelBatch
                );
            }
        );

        tabBatch.addEventListener(
            "click",
            function () {
                setActivePanel(
                    tabBatch,
                    panelBatch,
                    tabSingle,
                    panelSingle
                );
            }
        );
    }

    function setupSingleClaimForm() {
        var claimForm =
            document.getElementById(
                "realtime-claim-form"
            );

        if (!claimForm) {
            return;
        }

        claimForm.addEventListener(
            "submit",
            function (event) {
                event.preventDefault();

                var resultBox =
                    document.getElementById(
                        "api-result-output"
                    );

                resultBox.classList.remove("hidden");
                resultBox.textContent =
                    "Evaluating transaction...";

                var payload = {
                    claim: {
                        policyId:
                            document.getElementById(
                                "policyId"
                            ).value.trim(),

                        policyHolderId:
                            document.getElementById(
                                "policyHolderId"
                            ).value.trim(),

                        billedAmount:
                            parseFloat(
                                document.getElementById(
                                    "billedAmount"
                                ).value
                            ),

                        mainCategory:
                            "Inpatient Hospital Care",

                        subCategory:
                            "SURGERY",

                        dateOfService:
                            "2016-05-08"
                    },

                    policy: {
                        planId: "P001",

                        individualThreshold:
                            6000.0,

                        familyThreshold:
                            12000.0,

                        currentIndividualAccumulated:
                            parseFloat(
                                document.getElementById(
                                    "currentInd"
                                ).value
                            ),

                        currentFamilyAccumulated:
                            parseFloat(
                                document.getElementById(
                                    "currentFam"
                                ).value
                            ),

                        isActive: true
                    },

                    rule:
                    document.getElementById(
                        "ruleString"
                    ).value
                };

                fetchCSRFToken()
                    .then(function (token) {
                        return fetch(
                            "/bin/kp/claims/process",
                            {
                                method: "POST",

                                credentials:
                                    "same-origin",

                                headers: {
                                    "Content-Type":
                                        "application/json",

                                    "CSRF-Token":
                                    token
                                },

                                body:
                                    JSON.stringify(
                                        payload
                                    )
                            }
                        );
                    })
                    .then(function (response) {
                        return response.json()
                            .then(function (data) {
                                if (!response.ok) {
                                    throw new Error(
                                        data.error
                                        || "Claim processing failed."
                                    );
                                }

                                return data;
                            });
                    })
                    .then(function (data) {
                        if (data.errorCode) {
                            resultBox.textContent =
                                data.errorCode
                                + ": "
                                + data.errorMessage;

                            return;
                        }

                        resultBox.innerHTML =
                            "<strong>"
                            + "Adjudication Complete"
                            + "</strong><br>"
                            + "Plan Pays: $"
                            + Number(
                                data.planPays
                            ).toFixed(2)
                            + "<br>"
                            + "Policyholder Pays: $"
                            + Number(
                                data.policyHolderPays
                            ).toFixed(2)
                            + "<br>"
                            + "<small>"
                            + escapeHtml(
                                data.processingMessage
                                || ""
                            )
                            + "</small>";
                    })
                    .catch(function (error) {
                        resultBox.textContent =
                            error.message;
                    });
            }
        );
    }

    function setupBatchProcessing() {
        var dropzone =
            document.getElementById(
                "csv-dropzone"
            );

        var fileInput =
            document.getElementById(
                "batch-file-input"
            );

        var selectedFileName =
            document.getElementById(
                "selected-file-name"
            );

        var processButton =
            document.getElementById(
                "process-batch-button"
            );

        var statusBox =
            document.getElementById(
                "batch-status"
            );

        if (!dropzone
            || !fileInput
            || !processButton
            || !statusBox) {

            return;
        }

        function selectFile(file) {
            if (!file) {
                return;
            }

            if (!file.name.toLowerCase()
                .endsWith(".csv")) {

                fileInput.value = "";
                processButton.disabled = true;

                selectedFileName.textContent =
                    "Only CSV files are supported.";

                showBatchStatus(
                    statusBox,
                    "error",
                    "Please select a valid .csv file."
                );

                return;
            }

            if (file.size <= 0) {
                fileInput.value = "";
                processButton.disabled = true;

                showBatchStatus(
                    statusBox,
                    "error",
                    "The selected CSV file is empty."
                );

                return;
            }

            if (file.size > 10 * 1024 * 1024) {
                fileInput.value = "";
                processButton.disabled = true;

                showBatchStatus(
                    statusBox,
                    "error",
                    "The selected file exceeds "
                    + "the 10 MB demo limit."
                );

                return;
            }

            selectedFileName.textContent =
                file.name
                + " ("
                + formatFileSize(file.size)
                + ")";

            processButton.disabled = false;

            showBatchStatus(
                statusBox,
                "ready",
                "File selected. "
                + "Click Process and Download CSV."
            );
        }

        dropzone.addEventListener(
            "click",
            function () {
                fileInput.click();
            }
        );

        dropzone.addEventListener(
            "keydown",
            function (event) {
                if (event.key === "Enter"
                    || event.key === " ") {

                    event.preventDefault();
                    fileInput.click();
                }
            }
        );

        fileInput.addEventListener(
            "change",
            function () {
                selectFile(
                    fileInput.files[0]
                );
            }
        );

        dropzone.addEventListener(
            "dragover",
            function (event) {
                event.preventDefault();
                dropzone.classList.add(
                    "drag-active"
                );
            }
        );

        dropzone.addEventListener(
            "dragleave",
            function () {
                dropzone.classList.remove(
                    "drag-active"
                );
            }
        );

        dropzone.addEventListener(
            "drop",
            function (event) {
                event.preventDefault();

                dropzone.classList.remove(
                    "drag-active"
                );

                var files =
                    event.dataTransfer.files;

                if (!files || !files.length) {
                    return;
                }

                /*
                 * Assigning DataTransfer files to an input is supported
                 * by modern browsers used for the local demo.
                 */
                fileInput.files = files;
                selectFile(files[0]);
            }
        );

        processButton.addEventListener(
            "click",
            function () {
                var file =
                    fileInput.files[0];

                if (!file) {
                    showBatchStatus(
                        statusBox,
                        "error",
                        "Choose a CSV file first."
                    );

                    return;
                }

                processButton.disabled = true;
                processButton.textContent =
                    "Processing...";

                showBatchStatus(
                    statusBox,
                    "processing",
                    "Uploading and processing "
                    + file.name
                    + "..."
                );

                var formData =
                    new FormData();

                formData.append(
                    "file",
                    file,
                    file.name
                );

                fetchCSRFToken()
                    .then(function (token) {
                        return fetch(
                            "/bin/kp/claims/batch-process",
                            {
                                method: "POST",

                                credentials:
                                    "same-origin",

                                headers: {
                                    "CSRF-Token":
                                    token
                                },

                                body: formData
                            }
                        );
                    })
                    .then(function (response) {
                        var contentType =
                            response.headers.get(
                                "Content-Type"
                            ) || "";

                        if (!response.ok) {
                            if (contentType.indexOf(
                                "application/json"
                            ) !== -1) {

                                return response.json()
                                    .then(function (data) {
                                        throw new Error(
                                            data.error
                                            || "Batch processing failed."
                                        );
                                    });
                            }

                            return response.text()
                                .then(function (text) {
                                    throw new Error(
                                        text
                                        || "Batch processing failed."
                                    );
                                });
                        }

                        return response.blob();
                    })
                    .then(function (csvBlob) {
                        if (!csvBlob
                            || csvBlob.size === 0) {

                            throw new Error(
                                "The server returned "
                                + "an empty CSV."
                            );
                        }

                        downloadBlob(
                            csvBlob,
                            "output_claims.csv"
                        );

                        showBatchStatus(
                            statusBox,
                            "success",
                            "Processing completed. "
                            + "output_claims.csv "
                            + "has been downloaded."
                        );
                    })
                    .catch(function (error) {
                        showBatchStatus(
                            statusBox,
                            "error",
                            error.message
                        );
                    })
                    .finally(function () {
                        processButton.disabled = false;

                        processButton.textContent =
                            "Process and Download CSV";
                    });
            }
        );
    }

    function downloadBlob(blob, fileName) {
        var downloadUrl =
            URL.createObjectURL(blob);

        var anchor =
            document.createElement("a");

        anchor.href = downloadUrl;
        anchor.download = fileName;

        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();

        window.setTimeout(
            function () {
                URL.revokeObjectURL(
                    downloadUrl
                );
            },
            1000
        );
    }

    function showBatchStatus(
        statusBox,
        status,
        message
    ) {
        statusBox.classList.remove(
            "hidden",
            "status-ready",
            "status-processing",
            "status-success",
            "status-error"
        );

        statusBox.classList.add(
            "status-" + status
        );

        statusBox.textContent = message;
    }

    function formatFileSize(bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        }

        if (bytes < 1024 * 1024) {
            return (
                bytes / 1024
            ).toFixed(1) + " KB";
        }

        return (
            bytes / (1024 * 1024)
        ).toFixed(1) + " MB";
    }

    function escapeHtml(value) {
        var div =
            document.createElement("div");

        div.textContent =
            value == null ? "" : String(value);

        return div.innerHTML;
    }

    function initialize() {
        setupTabs();
        setupSingleClaimForm();
        setupBatchProcessing();
    }

    if (document.readyState === "loading") {
        document.addEventListener(
            "DOMContentLoaded",
            initialize
        );
    } else {
        initialize();
    }
})();
