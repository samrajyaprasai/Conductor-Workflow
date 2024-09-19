package org.example.local.workflow;

import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.WorkflowBuilder;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import java.time.Duration;

public class ExactMatchesReviewSubworkflow {

    // Method to create the exact matches review subworkflow
    public ConductorWorkflow<CaseRequest> createSubWorkflow(WorkflowExecutor executor) {

        // Initialize the workflow builder
        WorkflowBuilder<CaseRequest> workflowBuilder = new WorkflowBuilder<>(executor);

        // Human task for reviewing exact matches
        Wait exactMatchesReview = new Wait("human-task_exact_matches_review", Duration.ofMillis(200000))
                .input("reviewPrompt", "Please review the exact matches and enter matchIds.")
                .input("category", "EXACT_MATCHES");

        // Build the subworkflow
        ConductorWorkflow<CaseRequest> subWorkflow = workflowBuilder.name("ExactMatchesReviewSubworkflow")
                .version(1)
                .description("Subworkflow to review exact matches")
                .add(exactMatchesReview)  // Add the exact matches review task
                .output("matchIds", "${human-task_exact_matches_review.output.matchIds}")  // Set output for match IDs
                .build();

        // Register the subworkflow with the Conductor server
        subWorkflow.registerWorkflow(true, true);

        return subWorkflow;
    }
}
