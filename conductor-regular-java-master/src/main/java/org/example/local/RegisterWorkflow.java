package org.example.local;

import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import io.orkes.conductor.client.MetadataClient;
import io.orkes.conductor.client.OrkesClients;
import io.orkes.conductor.client.TaskClient;
import io.orkes.conductor.client.WorkflowClient;
import org.example.local.workflow.*;

public class RegisterWorkflow {

    public static void main(String[] args) {

        try {
            // Initialize Orkes clients to interact with the Conductor server
            OrkesClients orkesClients = ApiUtil.getOrkesClient();
            TaskClient taskClient = orkesClients.getTaskClient();
            WorkflowClient workflowClient = orkesClients.getWorkflowClient();
            MetadataClient metadataClient = orkesClients.getMetadataClient();

            // Create the WorkflowExecutor to register workflows and manage tasks
            WorkflowExecutor workflowExecutor = new WorkflowExecutor(taskClient, workflowClient, metadataClient, 10);

            // Register the PotentialMatchesReviewSubworkflow
            PotentialMatchesReviewSubworkflow potentialMatchesSubWorkflow = new PotentialMatchesReviewSubworkflow();
            ConductorWorkflow<CaseRequest> potentialMatchesWorkflow = potentialMatchesSubWorkflow.createSubWorkflow(workflowExecutor);
            potentialMatchesWorkflow.registerWorkflow(true, true);  // Register subworkflow
            System.out.println("Subworkflow registered: " + potentialMatchesWorkflow.getName() + " version: " + potentialMatchesWorkflow.getVersion());

            // Register the FalseMatchesReviewSubworkflow
            FalseMatchesReviewSubworkflow falseMatchesSubWorkflow = new FalseMatchesReviewSubworkflow();
            ConductorWorkflow<CaseRequest> falseMatchesWorkflow = falseMatchesSubWorkflow.createSubWorkflow(workflowExecutor);
            falseMatchesWorkflow.registerWorkflow(true, true);  // Register subworkflow
            System.out.println("Subworkflow registered: " + falseMatchesWorkflow.getName() + " version: " + falseMatchesWorkflow.getVersion());

            // Register the ExactMatchesReviewSubworkflow
            ExactMatchesReviewSubworkflow exactMatchesSubWorkflow = new ExactMatchesReviewSubworkflow();
            ConductorWorkflow<CaseRequest> exactMatchesWorkflow = exactMatchesSubWorkflow.createSubWorkflow(workflowExecutor);
            exactMatchesWorkflow.registerWorkflow(true, true);  // Register subworkflow
            System.out.println("Subworkflow registered: " + exactMatchesWorkflow.getName() + " version: " + exactMatchesWorkflow.getVersion());

            // Register the main ApprovalWorkflowWaitTasks (Main workflow)
            ApprovalWorkflowWaitTasks approvalWorkflowWaitTasks = new ApprovalWorkflowWaitTasks(workflowExecutor);
            ConductorWorkflow<CaseRequest> approvalWorkflow = approvalWorkflowWaitTasks.createApprovalWorkflow();
            approvalWorkflow.registerWorkflow(true, true);  // Register the main workflow
            System.out.println("Main workflow registered: " + approvalWorkflow.getName() + " version: " + approvalWorkflow.getVersion());

        } catch (Exception e) {
            // Handle any errors during workflow registration
            System.err.println("Error occurred during workflow registration: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Ensure the program exits after registration
            System.exit(0);
        }
    }
}
