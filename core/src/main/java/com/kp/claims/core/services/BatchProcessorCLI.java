package com.kp.claims.core.services;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BatchProcessorCLI {

    public static void main(String[] args) {

        if (args.length < 2) {
            System.out.println(
                    "Error: Missing input or output file argument.");

            System.out.println(
                    "Usage: BatchProcessorCLI "
                            + "<input.csv> <output.csv>");

            System.exit(1);
        }

        String inputFile = args[0];
        String outputFile = args[1];

        BatchProcessingService service =
                new BatchProcessingService();

        System.out.println(
                "Initializing KP Batch Pipeline "
                        + "Processing Module...");

        try (
                InputStream inputStream =
                        new FileInputStream(inputFile);

                OutputStream outputStream =
                        new FileOutputStream(outputFile)
        ) {
            int processedCount =
                    service.process(
                            inputStream,
                            outputStream);

            System.out.println(
                    "Processing Successful! "
                            + processedCount
                            + " transaction claims exported into: "
                            + outputFile);

        } catch (Exception e) {

            System.err.println(
                    "Fatal batch processing error: "
                            + e.getMessage());

            e.printStackTrace();
            System.exit(1);
        }
    }
}
