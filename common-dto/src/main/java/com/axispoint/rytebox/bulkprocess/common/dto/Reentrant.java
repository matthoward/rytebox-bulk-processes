package com.axispoint.rytebox.bulkprocess.common.dto;

/**
 * Reentrant defines a bulk process that can be executed iteratively, picking up from where it left off until it is complete.
 *
 * isDone is used in the AWS Step Function that calls the lambda until it reports as done.
 *
 * iteration is a counter that is incremented after each execution. This is primarily used to name files for output in a
 * sequential order, so a bulk export can output partial datasets until finally all the iteration files can be merged
 *
 * continuation is any kind of class that holds the necessary data for the process to know where it left off. For example
 * a SQL-based export might use an integer of the last id, while a file-based process might be the byte offset, and an ES-
 * based process would be a JSON array of sorting searchAfter terms
 *
 * each iteration should call completeIteration with this completion data as well as any exception messages
 * that may need to be captured
 *
 */
public interface Reentrant<C> {
    boolean isDone();

    int getIteration();

    C getContinuation();

    // TODO: we might need to also be able to output some data structure with successful metadata

    Reentrant<C> completeIteration(boolean isDone, C continuation, String exceptionMsg);
}
