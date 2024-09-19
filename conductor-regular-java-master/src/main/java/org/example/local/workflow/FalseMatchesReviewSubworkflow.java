package org.example.local.workflow;

import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.WorkflowBuilder;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import java.time.Duration;

public class FalseMatchesReviewSubworkflow {

    // Method to create the false matches review subworkflow
    public ConductorWorkflow<CaseRequest> createSubWorkflow(WorkflowExecutor executor) {

        // Initialize the workflow builder
        WorkflowBuilder<CaseRequest> workflowBuilder = new WorkflowBuilder<>(executor);

        // Human task for reviewing false matches
        Wait falseMatchesReview = new Wait("human-task_false_matches_review", Duration.ofMillis(200000))
                .input("reviewPrompt", "Please review the false matches and enter matchIds.")
                .input("category", "FALSE_MATCHES");

        // Build the subworkflow
        ConductorWorkflow<CaseRequest> subWorkflow = workflowBuilder.name("FalseMatchesReviewSubworkflow")
                .version(1)
                .description("Subworkflow to review false matches")
                .add(falseMatchesReview)  // Add the false matches review task
                .output("matchIds", "${human-task_false_matches_review.output.matchIds}")  // Set output for match IDs
                .build();

        // Register the subworkflow with the Conductor server
        subWorkflow.registerWorkflow(true, true);

        return subWorkflow;
    }
}
