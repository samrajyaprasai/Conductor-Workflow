package org.example.local.workers;

import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

public class ConductorWorkers {
    @WorkerTask("greet")
    public String greeting(@InputParam("name") String name) {
        return ("Hello " + name);
    }
}
