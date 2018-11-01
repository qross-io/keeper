package io.qross.model

import io.qross.util.DataRow

//case class Message(value: Any)

case class Tick(minute: String)

object TaskStatus {
    val NEW = "new"
    val INSTANT = "instant"
    val NO_COMMANDS = "no_commands"
    //val MANUAL = "manual"
    //val RESTARTING = "restarting"
    val INITIALIZED = "initialized"
    val READY = "ready"
    val EXECUTING = "executing"
    val CHECKING_LIMIT = "checking_limit"
    val FINISHED = "finished"
    val FAILED = "failed"
    val INCORRECT = "incorrect"
    val TIMEOUT = "timeout"
    val SUCCESS = "success"
    val EMPTY = ""
}

object ActionStatus {
    val WAITING = "waiting"
    val QUEUING = "queuing"
    val RUNNING = "running"
    val EXCEPTIONAL = "exceptional"
    val OVERTIME = "overtime"
    val DONE = "done"
}

case class Task(id: Long, var status: String = TaskStatus.INITIALIZED) {

    var jobId: Int = 0
    var taskTime: String = ""
    var recordTime: String = ""

    def of(jobId: Int): Task = {
        this.jobId = jobId
        this
    }

    def at(taskTime: String, recordTime: String): Task = {
        this.taskTime = taskTime
        this.recordTime = recordTime
        this
    }

    def INITIALIZED: Task = {
        this.status = TaskStatus.INITIALIZED
        this
    }
    
    def READY: Task = {
        this.status = TaskStatus.READY
        this
    }
    
    def EXECUTING: Task = {
        this.status = TaskStatus.EXECUTING
        this
    }
}

case class TaskCommand(row: DataRow)


