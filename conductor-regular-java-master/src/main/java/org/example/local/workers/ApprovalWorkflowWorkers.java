package org.example.local.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import java.util.Random;

public class ApprovalWorkflowWorkers {


    @WorkerTask(value = "add_operator_review", threadCount = 5)
    public @OutputParam("decision") String addReview(@InputParam("caseId") String caseId,
                                                     @InputParam("currentStage") String currentStage) {

        System.out.println("Adding review for caseId: " + caseId + " at stage: " + currentStage);

        if (Integer.parseInt(caseId) % 2 == 0) {
            return "TrueMatch";
        } else {
            return "NegativeMatch";
        }
    }

}
