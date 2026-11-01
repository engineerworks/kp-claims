(function () {
    "use strict";

    function fetchCSRFToken() {
        return fetch("/libs/granite/csrf/token.json", {
            credentials: "same-origin"
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error(
                        "Unable to retrieve AEM CSRF token."
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

    /*
     * ---------------------------------------------------------
     * TABS
     * ---------------------------------------------------------
     */

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

                tabSingle.classList.add("active");
                tabBatch.classList.remove("active");

                panelSingle.classList.remove("hidden");
                panelBatch.classList.add("hidden");
            }
        );

        tabBatch.addEventListener(
            "click",
            function () {

                tabBatch.classList.add("active");
                tabSingle.classList.remove("active");

                panelBatch.classList.remove("hidden");
                panelSingle.classList.add("hidden");
            }
        );
    }

    /*
     * ---------------------------------------------------------
     * SINGLE CLAIM / REST API
     * ---------------------------------------------------------
     */

    function setupSingleClaimForm() {

        var claimForm =
            document.getElementById(
                "realtime-claim-form"
            );

        var resultBox =
            document.getElementById(
                "api-result-output"
            );

        if (!claimForm || !resultBox) {
            return;
        }

        claimForm.addEventListener(
            "submit",
            function (event) {

                event.preventDefault();

                var policyId =
                    document.getElementById(
                        "policyId"
                    ).value.trim();

                var policyHolderId =
                    document.getElementById(
                        "policyHolderId"
                    ).value.trim();

                var dateOfService =
                    document.getElementById(
                        "dateOfService"
                    ).value;

                var billedAmount =
                    parseFloat(
                        document.getElementById(
                            "billedAmount"
                        ).value
                    );

                var mainCategory =
                    document.getElementById(
                        "mainCategory"
                    ).value;

                var subCategory =
                    document.getElementById(
                        "subCategory"
                    ).value;

                var individualAccumulated =
                    parseFloat(
                        document.getElementById(
                            "currentInd"
                        ).value
                    );

                var familyAccumulated =
                    parseFloat(
                        document.getElementById(
                            "currentFam"
                        ).value
                    );

                if (!policyId
                    || !policyHolderId
                    || !dateOfService
                    || !mainCategory
                    || !subCategory
                    || !Number.isFinite(billedAmount)
                    || billedAmount <= 0
                    || !Number.isFinite(
                        individualAccumulated
                    )
                    || individualAccumulated < 0
                    || !Number.isFinite(
                        familyAccumulated
                    )
                    || familyAccumulated < 0) {

                    showClaimError(
                        resultBox,
                        "Please provide valid values "
                        + "for all claim fields."
                    );

                    return;
                }

                /*
                 * The REST caller supplies the two accumulated
                 * deductible values as required by the assignment.
                 *
                 * Plan assignment, thresholds, active status and
                 * coverage rule are resolved on the server.
                 */
                var payload = {

                    claim: {
                        policyId: policyId,

                        policyHolderId:
                        policyHolderId,

                        billedAmount:
                        billedAmount,

                        mainCategory:
                        mainCategory,

                        subCategory:
                        subCategory,

                        dateOfService:
                        dateOfService
                    },

                    policy: {

                        currentIndividualAccumulated:
                        individualAccumulated,

                        currentFamilyAccumulated:
                        familyAccumulated
                    }
                };

                resultBox.classList.remove("hidden");

                resultBox.textContent =
                    "Processing claim...";

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

                            showClaimError(
                                resultBox,
                                data.errorCode
                                + ": "
                                + (
                                    data.errorMessage
                                    || "Claim processing failed."
                                )
                            );

                            return;
                        }

                        displayClaimResult(
                            resultBox,
                            data
                        );
                    })

                    .catch(function (error) {

                        showClaimError(
                            resultBox,
                            error.message
                        );
                    });
            }
        );
    }

    function displayClaimResult(
        resultBox,
        data
    ) {

        resultBox.classList.remove("hidden");

        resultBox.innerHTML =
            "<strong>Adjudication Complete</strong>"
            + "<br><br>"

            + "<strong>Coverage Rule:</strong> "
            + escapeHtml(
                data.ruleUsed || ""
            )
            + "<br>"

            + "<strong>Billed Amount:</strong> $"
            + formatAmount(
                data.billedAmount
            )
            + "<br>"

            + "<strong>Plan Pays:</strong> $"
            + formatAmount(
                data.planPays
            )
            + "<br>"

            + "<strong>Policyholder Pays:</strong> $"
            + formatAmount(
                data.policyHolderPays
            )
            + "<br><br>"

            + "<strong>"
            + "Individual Accumulated:"
            + "</strong> $"
            + formatAmount(
                data.updatedIndividualAccumulated
            )
            + "<br>"

            + "<strong>"
            + "Family Accumulated:"
            + "</strong> $"
            + formatAmount(
                data.updatedFamilyAccumulated
            )
            + "<br><br>"

            + "<strong>Processing Result:</strong> "
            + escapeHtml(
                data.processingMessage || ""
            );
    }

    function showClaimError(
        resultBox,
        message
    ) {

        resultBox.classList.remove("hidden");

        resultBox.innerHTML =
            "<strong>Claim could not be processed</strong>"
            + "<br>"
            + escapeHtml(message);
    }

    /*
     * ---------------------------------------------------------
     * BATCH PROCESSING
     * ---------------------------------------------------------
     */

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
            || !selectedFileName
            || !processButton
            || !statusBox) {

            return;
        }

        function selectFile(file) {

            if (!file) {
                return;
            }

            if (!file.name
                .toLowerCase()
                .endsWith(".csv")) {

                fileInput.value = "";

                processButton.disabled =
                    true;

                selectedFileName.textContent =
                    "No file selected";

                showBatchStatus(
                    statusBox,
                    "error",
                    "Only CSV files are supported."
                );

                return;
            }

            if (file.size <= 0) {

                fileInput.value = "";

                processButton.disabled =
                    true;

                selectedFileName.textContent =
                    "No file selected";

                showBatchStatus(
                    statusBox,
                    "error",
                    "The selected CSV is empty."
                );

                return;
            }

            /*
             * Demo safety limit.
             *
             * Large production files should be submitted to an
             * asynchronous job instead of keeping the HTTP request
             * open while processing.
             */
            if (file.size > 10 * 1024 * 1024) {

                fileInput.value = "";

                processButton.disabled =
                    true;

                selectedFileName.textContent =
                    "No file selected";

                showBatchStatus(
                    statusBox,
                    "error",
                    "The selected CSV exceeds "
                    + "the 10 MB demo limit."
                );

                return;
            }

            selectedFileName.textContent =
                file.name
                + " ("
                + formatFileSize(
                    file.size
                )
                + ")";

            processButton.disabled =
                false;

            showBatchStatus(
                statusBox,
                "ready",
                "File ready for processing."
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

                if (!files
                    || !files.length) {

                    return;
                }

                /*
                 * Do not depend on assigning to
                 * fileInput.files.
                 *
                 * Store the dropped file separately.
                 */
                fileInput._droppedFile =
                    files[0];

                selectFile(
                    files[0]
                );
            }
        );

        processButton.addEventListener(
            "click",
            function () {

                var selectedFile =
                    fileInput._droppedFile
                    || fileInput.files[0];

                if (!selectedFile) {

                    showBatchStatus(
                        statusBox,
                        "error",
                        "Choose a CSV file first."
                    );

                    return;
                }

                processButton.disabled =
                    true;

                processButton.textContent =
                    "Processing...";

                showBatchStatus(
                    statusBox,
                    "processing",
                    "Processing "
                    + selectedFile.name
                    + "..."
                );

                var formData =
                    new FormData();

                formData.append(
                    "file",
                    selectedFile,
                    selectedFile.name
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

                                /*
                                 * Do not manually set
                                 * Content-Type here.
                                 *
                                 * The browser adds the
                                 * multipart boundary.
                                 */
                                body:
                                formData
                            }
                        );
                    })

                    .then(function (response) {

                        var contentType =
                            response.headers.get(
                                "Content-Type"
                            ) || "";

                        if (!response.ok) {

                            if (
                                contentType.indexOf(
                                    "application/json"
                                ) !== -1
                            ) {

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
                                "The processing service "
                                + "returned an empty CSV."
                            );
                        }

                        downloadBlob(
                            csvBlob,
                            "output_claims.csv"
                        );

                        showBatchStatus(
                            statusBox,
                            "success",
                            "Processing complete. "
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

                        processButton.disabled =
                            false;

                        processButton.textContent =
                            "Process and Download CSV";
                    });
            }
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

        statusBox.textContent =
            message;
    }

    /*
     * ---------------------------------------------------------
     * COMMON UTILITIES
     * ---------------------------------------------------------
     */

    function downloadBlob(
        blob,
        fileName
    ) {

        var downloadUrl =
            URL.createObjectURL(blob);

        var anchor =
            document.createElement("a");

        anchor.href =
            downloadUrl;

        anchor.download =
            fileName;

        document.body.appendChild(
            anchor
        );

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

    function formatAmount(value) {

        var number =
            Number(value);

        if (!Number.isFinite(number)) {
            return "0.00";
        }

        return number.toFixed(2);
    }

    function formatFileSize(bytes) {

        if (bytes < 1024) {
            return bytes + " bytes";
        }

        if (bytes < 1024 * 1024) {

            return (
                    bytes / 1024
                ).toFixed(1)
                + " KB";
        }

        return (
                bytes / (
                    1024 * 1024
                )
            ).toFixed(1)
            + " MB";
    }

    function escapeHtml(value) {

        var div =
            document.createElement(
                "div"
            );

        div.textContent =
            value == null
                ? ""
                : String(value);

        return div.innerHTML;
    }

    /*
     * ---------------------------------------------------------
     * INITIALIZATION
     * ---------------------------------------------------------
     */

    function initialize() {
        setupTabs();
        setupSingleClaimForm();
        setupBatchProcessing();
    }

    if (
        document.readyState
        === "loading"
    ) {

        document.addEventListener(
            "DOMContentLoaded",
            initialize
        );

    } else {

        initialize();
    }

})();
