package org.example.local;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.MetadataClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.TaskClient;
import io.orkes.conductor.client.WorkflowClient;
import io.orkes.conductor.client.automator.TaskRunnerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class TriggerWorkers {

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {

        // Initialize Orkes clients to interact with Conductor
        OrkesClients orkesClients = ApiUtil.getOrkesClient();
        TaskClient taskClient = orkesClients.getTaskClient();
        WorkflowClient workflowClient = orkesClients.getWorkflowClient();
        MetadataClient metadataClient = orkesClients.getMetadataClient();

        // Create WorkflowExecutor for executing workflows
        WorkflowExecutor workflowExecutor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 10);

        // Initialize workers in the specified package
        workflowExecutor.initWorkers("org.example.local.workers");

        // Configure task runners to poll tasks
        TaskRunnerConfigurer taskrunner = initWorkers(Arrays.asList(), taskClient);

    }

    // Method to generate a random four-digit number (for caseId)
    public static int generateRandomFourDigitNumber() {
        Random random = new Random();
        return 1000 + random.nextInt(9000);
    }

    // Method to initialize and configure task runners
    private static TaskRunnerConfigurer initWorkers(List<Worker> workers, TaskClient taskClient) {
        TaskRunnerConfigurer.Builder builder = new TaskRunnerConfigurer.Builder(taskClient, workers);
        TaskRunnerConfigurer taskRunner = builder.withThreadCount(1) // Set thread count for task execution
                .withTaskPollTimeout(5) // Polling timeout in seconds
                .build();
        // Start polling tasks and execute them
        taskRunner.init();
        return taskRunner;
    }
}
