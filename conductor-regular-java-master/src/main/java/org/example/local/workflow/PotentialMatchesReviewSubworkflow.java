package org.example.local.workflow;

import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.WorkflowBuilder;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import java.time.Duration;

public class PotentialMatchesReviewSubworkflow {

    // Method to create the potential matches review subworkflow
    public ConductorWorkflow<CaseRequest> createSubWorkflow(WorkflowExecutor executor) {

        // Initialize the workflow builder
        WorkflowBuilder<CaseRequest> workflowBuilder = new WorkflowBuilder<>(executor);

        // Human task for reviewing potential matches
        Wait potentialMatchesReview = new Wait("human-task_potential_matches_review", Duration.ofMillis(200000))
                .input("reviewPrompt", "Please review the potential matches and enter matchIds.")
                .input("category", "POTENTIAL_MATCHES");

        // Build the subworkflow
        ConductorWorkflow<CaseRequest> subWorkflow = workflowBuilder.name("PotentialMatchesReviewSubworkflow")
                .version(1)
                .description("Subworkflow to review potential matches")
                .add(potentialMatchesReview)  // Add the potential matches review task
                .output("matchIds", "${human-task_potential_matches_review.output.matchIds}")  // Set output for match IDs
                .build();

        // Register the subworkflow with the Conductor server
        subWorkflow.registerWorkflow(true, true);

        return subWorkflow;
    }
}
