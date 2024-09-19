package org.example.local.workflow;

import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.WorkflowBuilder;
import com.netflix.conductor.sdk.workflow.def.tasks.*;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ApprovalWorkflowWaitTasks {

    private final WorkflowExecutor executor;

    // Constructor to initialize the workflow executor
    public ApprovalWorkflowWaitTasks(WorkflowExecutor executor) {
        this.executor = executor;
    }

    // Registers the main workflow
    public void registerMainWorkflow() {
        ConductorWorkflow<CaseRequest> approvalWorkflow = createApprovalWorkflow();
        approvalWorkflow.registerWorkflow(true, true);
    }

    // Creates and defines the main workflow
    public ConductorWorkflow<CaseRequest> createApprovalWorkflow() {

        // Initialize the workflow builder
        WorkflowBuilder<CaseRequest> workflowBuilder = new WorkflowBuilder<>(executor);

        // Define wait tasks for each human reviewer with specific durations
        Wait level1Investigator1 = new Wait("human-task_level1_investigator-review-1", Duration.ofMillis(2000000));
        Wait level1Investigator2 = new Wait("human-task_level1_investigator-review-2", Duration.ofMillis(2000000));
        Wait level2Investigator = new Wait("human-task_level2_investigator-review", Duration.ofMillis(2000000));
        Wait level3Investigator = new Wait("human-task_compliance_officer-review", Duration.ofMillis(2000000));

        // JavaScript function to concatenate decisions from level 1 investigators
        String concatenateLevel1Decisions = "function concatenateDecisions(level1Decision1, level1Decision2) {" +
                "    return level1Decision1 + '_' + level1Decision2;" +
                "}" +
                "concatenateDecisions('${human-task_level1_investigator-review-1.output.decision}', '${human-task_level1_investigator-review-2.output.decision}');";

        // Subworkflows for reviewing potential, false, and exact matches
        SubWorkflow potentialMatchesSubWorkflow = new SubWorkflow("potential_matches_subworkflow_task", "PotentialMatchesReviewSubworkflow", 1);
        SubWorkflow falseMatchesSubWorkflow = new SubWorkflow("false_matches_subworkflow_task", "FalseMatchesReviewSubworkflow", 1);
        SubWorkflow exactMatchesSubWorkflow = new SubWorkflow("exact_matches_subworkflow_task", "ExactMatchesReviewSubworkflow", 1);

        // Build the workflow structure
        ConductorWorkflow<CaseRequest> workflow = workflowBuilder.name("CaseApprovalWorkflowWithChildCases")
                .version(1)
                .ownerEmail("caseTeam@org.com")
                .variables(new CaseWorkflowState())
                .description("Case approval workflow with child workflows")

                // Set initial case metadata state
                .add(new SetVariable("set_case_metadata_state_in_wf")
                        .input("caseId", "${workflow.input.caseId}")
                        .input("caseState", "IN_PROGRESS")
                        .input("caseType", "${workflow.input.caseType}")
                        .input("caseDescription", "${workflow.input.caseDescription}")
                        .input("currentLevel", "Level1")
                )

                // Fork to allow both level 1 investigators to review the case in parallel
                .add(new ForkJoin("level1_review",
                        new Task[]{level1Investigator1},
                        new Task[]{level1Investigator2})
                )

                // Concatenate level 1 decisions using JavaScript
                .add(new Javascript("concatenate_level1_decisions", concatenateLevel1Decisions))

                // Switch case to handle the decision outcome of level 1 reviewers
                .add(new Switch("evaluate_level1_decisions", "${concatenate_level1_decisions.output.result}")
                        .switchCase("NegativeMatch_NegativeMatch", new Terminate("level1_close_case", WorkflowStatus.COMPLETED,
                                "Both level 1 investigators marked as Negative. Case closed."))
                        .switchCase("TrueMatch_NegativeMatch", new SetVariable("TrueMatch_NegativeMatch_Set")
                                .input("caseState", "LEVEL_2_IN_PROGRESS")
                                .input("currentLevel", "Level2"))
                        .switchCase("NegativeMatch_TrueMatch", new SetVariable("NegativeMatch_TrueMatch_Set")
                                .input("caseState", "LEVEL_2_IN_PROGRESS")
                                .input("currentLevel", "Level2"))
                        .switchCase("TrueMatch_TrueMatch", new SetVariable("TrueMatch_TrueMatch_Set")
                                .input("caseState", "LEVEL_2_IN_PROGRESS")
                                .input("currentLevel", "Level2"))
                        .defaultCase(new Terminate("level1_unexpected_decision", WorkflowStatus.FAILED,
                                "Level 1 Unexpected decision combination",
                                getTerminateOutputMap()))
                )

                // Level 2 investigator's review task
                .add(level2Investigator)

                // Switch case to handle level 2 investigator's decision
                .add(new Switch("level_2_decision", "${human-task_level2_investigator-review.output.decision}")
                        .switchCase("NegativeMatch", new Terminate("level2_close_case", WorkflowStatus.COMPLETED,
                                "Level 2 investigator marked as Negative. Case closed.",
                                getTerminateOutputMap()))
                        .switchCase("TrueMatch", new SetVariable("level2_true_match_state_update")
                                .input("caseState", "LEVEL_2_FINISHED")
                                .input("currentLevel", "Level2"))
                        .defaultCase(new Terminate("level2_unexpected_decision", WorkflowStatus.FAILED,
                                "Level 2 Unexpected decision combination",
                                getTerminateOutputMap()))
                )

                // Fork to run all subworkflows (potential, false, and exact matches) in parallel
                .add(new ForkJoin("fork_match_review_subworkflows",
                        new Task[]{potentialMatchesSubWorkflow},
                        new Task[]{falseMatchesSubWorkflow},
                        new Task[]{exactMatchesSubWorkflow})
                )

                // Join to combine the outputs of all match review subworkflows
                .add(new Join("join_match_review_subworkflows",
                        new String[]{"potential_matches_subworkflow_task", "false_matches_subworkflow_task", "exact_matches_subworkflow_task"})
                )

                // Set variable to capture the combined output of all match reviews
                .add(new SetVariable("capture_combined_subworkflow_output")
                        .input("combinedReviewResults", "{ 'potentialMatches': '${potential_matches_subworkflow_task.output.matchIds}', "
                                + "'falseMatches': '${false_matches_subworkflow_task.output.matchIds}', "
                                + "'exactMatches': '${exact_matches_subworkflow_task.output.matchIds}' }")
                )

                // Level 3 investigator (compliance officer) review task
                .add(level3Investigator)

                // Switch case to handle the compliance officer's decision
                .add(new Switch("compliance_officer_decision", "${human-task_compliance_officer-review.output.decision}")
                        .switchCase("NegativeMatch", new Terminate("level3_negative_match_close_case", WorkflowStatus.COMPLETED,
                                "The compliance officer marked the case as Negative. Case closed.",
                                getTerminateOutputMap()))
                        .switchCase("TrueMatch", new Terminate("level3_true_match_close_case", WorkflowStatus.COMPLETED,
                                "The compliance officer marked the case as True match. Case closed.",
                                getTerminateOutputMap()))
                        .defaultCase(new Terminate("level3_unexpected_decision", WorkflowStatus.FAILED,
                                "Level 3 Unexpected decision combination",
                                getTerminateOutputMap()))
                )

                .build();

        // Register the workflow with the Conductor server
        workflow.registerWorkflow(true, true);

        return workflow;
    }

    // Helper method to generate the output map for case termination
    private Map<String, Object> getTerminateOutputMap() {
        Map<String, Object> output = new HashMap<>();
        output.put("caseId", "${workflow.input.caseId}");
        output.put("caseType", "${workflow.input.caseType}");
        output.put("caseDescription", "${workflow.input.caseDescription}");
        output.put("caseStatus", "${workflow.variables.caseState}");
        output.put("subworkflowOutput", "${workflow.variables.combinedReviewResults}");
        return output;
    }
}
