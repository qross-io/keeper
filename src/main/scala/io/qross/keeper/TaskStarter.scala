package io.qross.keeper
import akka.actor.Props
import akka.routing.BalancingPool
import io.qross.model._

class TaskStarter extends WorkActor {
    
    private val executor = context.actorOf(Props[TaskExecutor].withRouter(new BalancingPool(Global.CORES * Global.CONCURRENT_BY_CPU_CORES)), "executor")
    
    override def beat(tick: String): Unit = {
        //super.beat(tick)
        executor ! Tick(tick)
        QrossTask.checkTasksStatus(tick)
        //QrossTask.checkOvertimeOfActions(tick)
    }
    
    override def execute(task: Task): Unit = {
        QrossAction.getTaskCommandsToExecute(task)
            .foreach(row => executor ! TaskCommand(row))
                .clear()
    }
}
