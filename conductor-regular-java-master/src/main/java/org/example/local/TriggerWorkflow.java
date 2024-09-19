package org.example.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.MetadataClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.TaskClient;
import io.orkes.conductor.client.WorkflowClient;
import io.orkes.conductor.client.automator.TaskRunnerConfigurer;
import org.example.local.workflow.CaseRequest;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class TriggerWorkflow {

    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {

        // Initialize ObjectMapper and Orkes clients
        ObjectMapper objectMapper = new ObjectMapper();
        OrkesClients orkesClients = ApiUtil.getOrkesClient();
        TaskClient taskClient = orkesClients.getTaskClient();
        WorkflowClient workflowClient = orkesClients.getWorkflowClient();
        MetadataClient metadataClient = orkesClients.getMetadataClient();

        // Create WorkflowExecutor for executing workflows
        WorkflowExecutor workflowExecutor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 10);

        // Create a CaseRequest object with demo data
        CaseRequest caseRequest = new CaseRequest();
        String caseId = UUID.randomUUID().toString();  // Generate a random case ID
        caseRequest.setCaseId(caseId);
        caseRequest.setCaseType("DEMO");
        caseRequest.setCaseStatus("INITIALIZED");
        caseRequest.setCaseDescription("This is a demo case");

        // Convert case request to a map for workflow input
        Map<String, Object> inputMap = objectMapper.convertValue(caseRequest, Map.class);

        // Define the workflow name and version
        String workflowName = "CaseApprovalWorkflowWithChildCases";
        int version = 1;

        // Prepare the workflow start request
        StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest();
        startWorkflowRequest.setName(workflowName);
        startWorkflowRequest.setVersion(version);
        startWorkflowRequest.setCorrelationId("corr-" + caseId);  // Set correlation ID
        startWorkflowRequest.setInput(inputMap);

        // Start the workflow and capture the workflow ID
        String workflowId = workflowClient.startWorkflow(startWorkflowRequest);
        System.out.println("Workflow Id: " + workflowId);

        // Exit the program after starting the workflow
        System.exit(0);
    }

    // Initialize and configure task runners
    private static TaskRunnerConfigurer initWorkers(List<Worker> workers, TaskClient taskClient) {
        TaskRunnerConfigurer.Builder builder = new TaskRunnerConfigurer.Builder(taskClient, workers);
        TaskRunnerConfigurer taskRunner = builder.withThreadCount(1)   // Set thread count for worker tasks
                .withTaskPollTimeout(5)  // Polling timeout in seconds
                .build();
        // Start polling for tasks and execute them
        taskRunner.init();
        return taskRunner;
    }
}
