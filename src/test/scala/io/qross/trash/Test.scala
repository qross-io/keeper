package io.qross.trash


import io.qross.model.{ActionStatus, QrossTask, TaskOverall, TaskStatus}
import io.qross.util.Json.ListExt
import io.qross.util._

import scala.collection.mutable.ArrayBuffer

case class Test(var name: String = "", list: ArrayBuffer[Int] = new ArrayBuffer[Int]()) {

    name = "tom"
    list += 1
    list += 2
    list += 3
}

object Test {
    def main(args: Array[String]): Unit = {

        val dh = new DataHub()
        //stuck tasks
        //条件：task正在执行, 所有upstream_ids为空的dag记录
        dh.get(s"select id, job_id, task_id, command_id, upstream_ids, record_time, UNIX_TIMESTAMP(NOW()) - UNIX_TIMESTAMP(update_time) AS span, status FROM qross_tasks_dags WHERE task_id IN (SELECT id FROM qross_tasks WHERE status='${TaskStatus.EXECUTING}')")
            .cache("dags")
        dh.openCache()
            .get(s"SELECT DISTINCT task_id FROM dags WHERE status NOT IN ('${ActionStatus.DONE}', '${ActionStatus.WAITING}')")
                .put("DELETE FROM dags WHERE task_id=#task_id")
                .set("DELETE FROM dags WHERE upstream_ids<>''")
            .get("SELECT job_id, task_id, record_time FROM (SELECT job_id, task_id, record_time, MIN(span) AS span FROM dags GROUP BY job_id, task_id, record_time) A WHERE span>300")
                .put("INSERT INTO qross_stuck_records (job_id, task_id, record_time) VALUES (#job_id, #task_id, '#record_time') ON DUPLICATE KEY UPDATE check_times=check_times+1")
        if (dh.nonEmpty) {
            dh.openDefault()
                .get("SELECT task_id FROM qross_stuck_records WHERE check_times>=3")
                    .put(s"UPDATE qross_tasks SET status='${TaskStatus.READY}' WHERE id=#task_id AND status='${TaskStatus.EXECUTING}'")
        }

        dh.close()

        /*QrossTask.createInstantTask("1234567",
            """{
              "jobId": 1,
               "dag": "",
               "params": "",
               "commands": "",
               "delay": 5
              }""") */

        //Output.writeLine(OpenResourceFile("/templates/new.html").toString)

        //TaskOverall.of(1700L).store()

        //Output.writeMessage("NEXT TICK: " + CronExp.parse("0 0 1 L * ? *").getNextTick(DateTime.now))

        //writeMessage("NEXT TICK: " + CronExp.parse("0 58 7/2 * * FRI *").getNextTick(dateTime))

        //writeMessage("NEXT TICK: " + CronExp.parse("0 7 8,10 * * ? *").getNextTick(dateTime))

        //val list = List[String]("1", "2", "3")

        //val dh = new DataHub()
        //dh.close()
        /*println(DateTime.now.getString("yyyyMMdd/HH"))

        //QrossTask.checkTaskDependencies(542973L)
        //println(DateTime.of(2018, 3, 1).toEpochSecond)

        HDFS.list(args(1)).foreach(hdfs => {
            val reader = new HDFSReader(hdfs.path)
            var line = ""
            var count = 0
            while(reader.hasNextLine) {
                line = reader.readLine
                count += 1
            }
            reader.close()

            println(hdfs.path + " # " + count)
        })
        */

    }
}
