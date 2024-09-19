package org.example.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.run.Workflow;
import io.orkes.conductor.client.MetadataClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.TaskClient;
import io.orkes.conductor.client.WorkflowClient;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

public class HumanTaskProcess {

    public static void main(String[] args) throws InterruptedException {

        // Initialize ObjectMapper and Orkes clients
        ObjectMapper objectMapper = new ObjectMapper();
        OrkesClients orkesClients = ApiUtil.getOrkesClient();
        TaskClient taskClient = orkesClients.getTaskClient();
        WorkflowClient workflowClient = orkesClients.getWorkflowClient();
        MetadataClient metadataClient = orkesClients.getMetadataClient();

        // Get workflow ID from the user
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the workflow ID: ");
        String workflowId = scanner.nextLine();

        Workflow wf = workflowClient.getWorkflow(workflowId, false);
        Workflow.WorkflowStatus status = wf.getStatus();

        // Process tasks while workflow is running
        while (status != Workflow.WorkflowStatus.COMPLETED && status != Workflow.WorkflowStatus.FAILED) {
            wf = workflowClient.getWorkflow(workflowId, true);
            List<Task> tasks = wf.getTasks();
            processHumanTasks(taskClient, objectMapper, workflowId, tasks);  // Process human tasks
            status = getWorkflowStatus(workflowClient, workflowId);
            Thread.sleep(1000);  // Pause between task processing
        }

        // Fetch the workflow after completion to get the output
        wf = workflowClient.getWorkflow(workflowId, true);

        // Fetch and display the final output of the workflow
        Map<String, Object> finalOutput = wf.getOutput();
        System.out.println("Workflow Final Status :: " + status.name() + " for workflow id: " + workflowId);

        System.out.println("Workflow Final Output: ");
        if (finalOutput != null && !finalOutput.isEmpty()) {
            finalOutput.forEach((key, value) -> System.out.println(key + ": " + value));
        } else {
            System.out.println("No output data available.");
        }
    }

    // Print the workflow output in a specific order
    private static void printOutputInOrder(Map<String, Object> finalOutput) {
        if (finalOutput.containsKey("caseId")) {
            System.out.println("caseId: " + finalOutput.get("caseId"));
        }
        if (finalOutput.containsKey("caseType")) {
            System.out.println("caseType: " + finalOutput.get("caseType"));
        }
        if (finalOutput.containsKey("caseStatus")) {
            System.out.println("caseStatus: " + finalOutput.get("caseStatus"));
        }
        if (finalOutput.containsKey("caseDescription")) {
            System.out.println("caseDescription: " + finalOutput.get("caseDescription"));
        }
        if (finalOutput.containsKey("subworkflowOutput")) {
            System.out.println("matches: " + finalOutput.get("subworkflowOutput"));
        }
    }

    // Process all human tasks (both investigator decisions and match reviews)
    private static void processHumanTasks(TaskClient taskClient, ObjectMapper objectMapper, String wfId, List<Task> tasks) {
        Scanner scanner = new Scanner(System.in);

        // Filter and process only human tasks (e.g., investigator decisions, match reviews)
        List<Task> humanTasks = tasks.stream()
                .filter(task -> task.getStatus() == Task.Status.IN_PROGRESS && task.getReferenceTaskName().startsWith("human-task"))
                .collect(Collectors.toList());

        // Iterate over each human task and process based on task type
        humanTasks.forEach(task -> {
            String taskType = task.getReferenceTaskName();

            if (taskType.contains("potential_matches_review") ||
                    taskType.contains("false_matches_review") ||
                    taskType.contains("exact_matches_review")) {

                System.out.println("Match review task detected for " + taskType);
                processMatchReviewTask(taskClient, objectMapper, wfId, task, scanner);

            } else {
                // Handle investigator decision tasks
                processInvestigatorDecision(taskClient, objectMapper, wfId, task, scanner);
            }
        });
    }

    // Process investigator decision tasks
    private static void processInvestigatorDecision(TaskClient taskClient, ObjectMapper objectMapper, String wfId, Task task, Scanner scanner) {
        System.out.println("Enter the Decision for the Task: " + task.getReferenceTaskName() + " id: " + task.getTaskId());
        String decision = scanner.nextLine();

        // Create task result for the investigator decision
        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(task.getTaskId());
        taskResult.setWorkflowInstanceId(wfId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        ObjectNode objectNode = objectMapper.createObjectNode();
        objectNode.put("decision", decision);

        taskResult.setOutputData((Map) objectMapper.convertValue(objectNode, Map.class));
        taskClient.updateTask(taskResult);  // Update the task with the decision
    }

    // Process match review tasks for potential, false, and exact matches
    private static void processMatchReviewTask(TaskClient taskClient, ObjectMapper objectMapper, String wfId, Task task, Scanner scanner) {
        String taskType = task.getReferenceTaskName();

        // Get the category based on task type (e.g., potential, false, exact matches)
        String category = getCategoryFromTask(taskType);

        // Prompt user to enter match IDs for the task
        System.out.println("Enter the match IDs for " + category + " (comma-separated): ");
        String matchIdsInput = scanner.nextLine();
        String[] matchIds = matchIdsInput.split(",");

        // Create task result for match review
        TaskResult taskResult = new TaskResult();
        taskResult.setTaskId(task.getTaskId());
        taskResult.setWorkflowInstanceId(wfId);
        taskResult.setStatus(TaskResult.Status.COMPLETED);

        // Prepare the output data with match IDs
        ObjectNode outputData = objectMapper.createObjectNode();
        ArrayNode matchIdsArray = objectMapper.createArrayNode();
        for (String matchId : matchIds) {
            matchIdsArray.add(matchId.trim());
        }

        outputData.set("matchIds", matchIdsArray);  // Add match IDs to output data
        taskResult.setOutputData((Map<String, Object>) objectMapper.convertValue(outputData, Map.class));

        // Update the task with the collected output
        taskClient.updateTask(taskResult);
    }

    // Helper method to get the category based on the task reference name
    private static String getCategoryFromTask(String taskType) {
        if (taskType.contains("potential")) {
            return "Potential Matches";
        } else if (taskType.contains("false")) {
            return "False Matches";
        } else if (taskType.contains("exact")) {
            return "Exact Matches";
        }
        return "Unknown Category";
    }

    // Method to get the current status of the workflow
    private static Workflow.WorkflowStatus getWorkflowStatus(WorkflowClient workflowClient, String workflowId) {
        Workflow wf = workflowClient.getWorkflow(workflowId, false);
        return wf.getStatus();
    }
}
