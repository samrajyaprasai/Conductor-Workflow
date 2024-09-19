package org.example.local.workflow;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.WorkflowBuilder;
import com.netflix.conductor.sdk.workflow.def.tasks.*;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;
import com.netflix.conductor.sdk.workflow.def.tasks.Switch;
import com.netflix.conductor.sdk.workflow.def.tasks.SubWorkflow;

public class ApprovalWorkflow {

    private final WorkflowExecutor executor;

    // Constructor to initialize the workflow executor
    public ApprovalWorkflow(WorkflowExecutor executor) {
        this.executor = executor;
    }

    // Registers the main workflow
    public void registerMainWorkflow() {
        ConductorWorkflow<CaseRequest> approvalWorkflow = createApprovalWorkflow();
        approvalWorkflow.registerWorkflow(true, true);  // Register workflow with the Conductor server
    }

    // Define the main workflow
    public ConductorWorkflow<CaseRequest> createApprovalWorkflow() {

        WorkflowBuilder<CaseRequest> workflowBuilder = new WorkflowBuilder<>(executor);

        // Define tasks for human reviews (investigators at each level)
        SimpleTask level1Investigator1 = new SimpleTask("add_operator_review", "level1_investigator_review_1")
                .input("caseId", "${workflow.input.caseId}")
                .input("currentStage", "level1_investigator_review_1");

        SimpleTask level1Investigator2 = new SimpleTask("add_operator_review", "level1_investigator_review_2")
                .input("caseId", "${workflow.input.caseId}")
                .input("currentStage", "level1_investigator_review_2");

        SimpleTask level2Investigator = new SimpleTask("add_operator_review", "level2_investigator_review")
                .input("caseId", "${workflow.input.caseId}")
                .input("currentStage", "level2_investigator_review_1");

        SimpleTask level3Investigator = new SimpleTask("add_operator_review", "level3_investigator_review")
                .input("caseId", "${workflow.input.caseId}")
                .input("currentStage", "level3_investigator_review_1");

        // JavaScript to concatenate decisions from level 1 investigators
        String concatenateLevel1Decisions = "function concatenateDecisions(level1Decision1, level1Decision2) {" +
                "    return level1Decision1 + '_' + level1Decision2;" +
                "}" +
                "concatenateDecisions('${level1_investigator_review_1.output.decision}', '${level1_investigator_review_2.output.decision}');";

        // Define subworkflows for child cases
        SubWorkflow childCase1 = new SubWorkflow("child_case_1", "child_case_review_subworkflow", 1);
        SubWorkflow childCase2 = new SubWorkflow("child_case_2", "child_case_review_subworkflow", 1);
        SubWorkflow childCase3 = new SubWorkflow("child_case_3", "child_case_review_subworkflow", 1);

        // Fork task to run all child workflows in parallel
        ForkJoin childCaseFork = new ForkJoin("fork_child_case_reviews",
                new Task[]{childCase1, childCase2, childCase3});

        // Build the workflow
        ConductorWorkflow<CaseRequest> workflow = workflowBuilder.name("CaseApprovalWorkflow")
                .version(1)
                .ownerEmail("caseTeam@org.com")
                .variables(new CaseWorkflowState())
                .description("Case approval workflow")

                // Set initial case metadata
                .add(new SetVariable("set_case_metadata_state_in_wf")
                        .input("caseId", "${workflow.input.caseId}")
                        .input("caseState", "IN_PROGRESS")
                        .input("caseType", "${workflow.input.caseType}")
                        .input("currentLevel", "Level1")
                )

                // Fork task for level 1 investigator reviews
                .add(new ForkJoin("level1_review",
                        new Task[]{level1Investigator1},
                        new Task[]{level1Investigator2})
                )

                // Concatenate decisions of level 1 investigators
                .add(new Javascript("concatenate_level1_decisions", concatenateLevel1Decisions))

                // Switch based on level 1 investigators' decisions
                .add(new Switch("evaluate_level1_decisions", "${concatenate_level1_decisions.output.result}")
                        .switchCase(
                                "NegativeMatch_NegativeMatch",
                                new SetVariable("closing the case"),
                                new Terminate("level1_close_case",
                                        Workflow.WorkflowStatus.COMPLETED,
                                        "Both level 1 investigators marked as a Negative match. Case closed.",
                                        "${concatenate_level1_decisions.output}")
                        )
                        .switchCase(
                                "TrueMatch_NegativeMatch",
                                new SetVariable("TrueMatch_NegativeMatch_Set")
                                        .input("caseState", "LEVEL_2_IN_PROGRESS")
                                        .input("currentLevel", "Level2")
                        )
                        .switchCase(
                                "NegativeMatch_TrueMatch",
                                new SetVariable("NegativeMatch_TrueMatch_Set")
                                        .input("caseState", "LEVEL_2_IN_PROGRESS")
                                        .input("currentLevel", "Level2")
                        )
                        .switchCase(
                                "TrueMatch_TrueMatch",
                                new SetVariable("TrueMatch_TrueMatch_Set")
                                        .input("caseState", "LEVEL_2_IN_PROGRESS")
                                        .input("currentLevel", "Level2")
                        )
                        .defaultCase(
                                new Terminate("level1_unexpected_decision",
                                        Workflow.WorkflowStatus.FAILED,
                                        "Level 1 Unexpected decision combination")
                        )
                )

                // Task for level 2 investigator review
                .add(level2Investigator)

                // Switch based on level 2 investigator decision
                .add(new Switch("level_2_decision", "${level2_investigator_review.output.decision}")
                        .switchCase(
                                "NegativeMatch",
                                new SetVariable("level2_negative_match_state_update")
                                        .input("caseState", "LEVEL_2_FINISHED")
                                        .input("currentLevel", "Level2")
                        )
                        .switchCase(
                                "TrueMatch",
                                new SetVariable("level2_true_match_state_update")
                                        .input("caseState", "LEVEL_2_FINISHED")
                                        .input("currentLevel", "Level2")
                        )
                        .defaultCase(
                                new Terminate("level2_unexpected_decision",
                                        Workflow.WorkflowStatus.FAILED,
                                        "Level 2 Unexpected decision combination")
                        )
                )

                // Task for level 3 investigator review
                .add(level3Investigator)

                // Switch based on level 3 investigator decision
                .add(new Switch("level3_decision", "${level3_investigator_review.output.decision}")
                        .switchCase(
                                "NegativeMatch",
                                new SetVariable("closing the case")
                                        .input("caseState", "LEVEL_3_FINISHED")
                                        .input("currentLevel", "Level3"),
                                new Terminate("level3_close_case",
                                        Workflow.WorkflowStatus.COMPLETED,
                                        "Level 3 investigator marked as a Negative match. Case closed.",
                                        "${level3_investigator_review.output.decision}")
                        )
                        .switchCase(
                                "TrueMatch",
                                new SetVariable("level3_true_match_state_update")
                                        .input("caseState", "LEVEL_3_FINISHED")
                                        .input("currentLevel", "Level3"),

                                // Fork child case reviews
                                childCaseFork,

                                // Complete parent workflow after child workflows finish
                                new Terminate("level3_true_match_close_case",
                                        Workflow.WorkflowStatus.COMPLETED,
                                        "Level 3 investigator marked as a True match. Case closed.",
                                        "${level3_investigator_review.output.decision}")
                        )
                        .defaultCase(
                                new Terminate("level3_unexpected_decision",
                                        Workflow.WorkflowStatus.FAILED,
                                        "Level 3 Unexpected decision combination")
                        )
                )

                // Finalize the workflow definition
                .build();

        return workflow;
    }
}
