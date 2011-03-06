/*
 * Copyright (c) 2011 Subbu Allamaraju
 *
 * This file is licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tini.core;

import java.util.ArrayList;
import java.util.List;

/**
 * This interface allows asynchronous tasks to depend on each other towards meeting a goal.
 *
 * Tasks are not monitored after starting. It is up to the task to use defensive coding practices
 * to return as quickly as possible.
 *
 * Further, tasks must ensure to pass results/errors to dependent tasks by calling #notifyDone. If a
 * task forgets to notify a dependent, the dependent may wait forever.
 *
 */
public abstract class AsyncTask {

    private List<AsyncTask> dependencies = new ArrayList<AsyncTask>();
    private String name;

    protected AsyncTask(String name) {
        this.name = name;
    }

    /**
     * Start execution of the task. Make sure to avoid blocking code.
     */
    public void start() {
    }

    /**
     * Take a result or an error from a dependent task
     *
     * @param name source of the result
     * @param result result
     * @param error  error
     */
    public void take(String name, Object result, Throwable error) {
    }

    /**
     * Register another task as a dependent.
     *
     * @param tasks dependent tasks
     */
    public final void dependsOn(AsyncTask... tasks) {
        for(AsyncTask task : tasks) {
            task.dependencies.add(this);
        }
    }

    /**
     * Subclasses must call this method to notify all the dependents.
     *
     * @param result result
     * @param error error
     */
    protected final void notifyDone(Object result, Throwable error) {
        for(AsyncTask dependent : dependencies) {
            dependent.take(this.name, result, error);
        }
    }
}
