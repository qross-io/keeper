package io.qross.keeper

import io.qross.model._
import io.qross.util.{DateTime, Output, Properties, Timer}

class Messager extends WorkActor {

    private val producer = context.actorSelection("akka://keeper/user/producer")
    private val processor = context.actorSelection("akka://keeper/user/processor")
    //private val starter = context.actorSelection("akka://keeper/user/starter")
    
    override def beat(tick: String): Unit = {
        super.beat(tick)
        
        val nextMinute = DateTime(tick).plusMinutes(1).toEpochSecond
        do {
            MessageBox.check().foreach(row => {
                val queryId = row.getString("query_id")
                val sender = row.getString("sender")
                val messageType = row.getString("message_type").toUpperCase
                val messageKey = row.getString("message_key").toUpperCase
                val messageText = row.getString("message_text")
                Output.writeDebugging(s"Got A Message # $queryId # $messageType # $messageKey # $messageText")

                messageType match {
                    case "GLOBAL" => Global.CONFIG.set(messageKey, messageText)
                    case "JOB" =>
                        //JOB - COMPLEMENT - job_id
                        //JOB - MANUAL - job_id:begin_time#end_time
                        //JOB - CREATE -
                        // {
                        //      "project_id": 0,
                        //      "title": "",
                        //      "cron_exp": "",
                        //      "closing_time": "",
                        //      "command_text": ""
                        // }
                        messageKey match {
                            case "COMPLEMENT" => QrossJob.tickTasks(messageText.toInt, queryId)
                            case "MANUAL" => QrossJob.manualTickTasks(messageText, queryId)
                            case _ =>
                        }
                    case "TASK" =>
                        //TASK - RESTART - WHOLE@TaskID - WHOLE@123
                        //TASK - RESTART - ^CommandIDs@TaskID - ^1,2,3,4,5@123
                        //TASK - RESTART - ^EXCEPTIONAL@TaskID - ^EXCEPTIONAL@123
                        //TASK - RESTART - CommandIDs@TaskID - 1,2,3,4,5@123
                        //TASK - INSTANT -
                            /*
                            {
                                jobId: 123,
                                dag: "1,2,3",
                                params: "name1:value1,name2:value2",
                                commands: "commandId:commandText##$##commandId:commandText",
                                delay: 0,
                                start_time: 'yyyyMMddHHmm00'
                            }
                             */
                        messageKey match {
                            case "RESTART" => producer ! QrossTask.restartTask(messageText.substring(messageText.indexOf("@") + 1).toLong, messageText.substring(0, messageText.indexOf("@")))
                            case "INSTANT" => QrossTask.createInstantTask(queryId, messageText) match {
                                                case Some(task) => producer ! task
                                                case None =>
                                            }
                            case _ =>
                        }
                    case "NOTE" =>
                        messageKey match {
                            //case "PROCESS" => QrossNote.processNote(sender, messageText.toLong)
                            case _ =>
                        }
                    case "USER" =>
                        messageKey match {
                            case "MASTER" => Global.CONFIG.set("MASTER_USER_GROUP", QrossUser.getUsers("master"))
                            case "KEEPER" => Global.CONFIG.set("KEEPER_USER_GROUP", QrossUser.getUsers("keeper"))
                            case _ =>
                        }

                    case "PROPERTIES" =>
                        messageKey match {
                            //PROPERTIES - ADD - local|resources:path
                            //PROPERTIES - UPDATE - id#local|resources:path
                            //PROPERTIES - REMOVE - id
                            //PROPERTIES - REFRESH - local|resources:path
                            case "ADD" => Properties.addFile(messageText.substring(0, messageText.indexOf(":")), messageText.substring(messageText.indexOf(":") + 1))
                            case "UPDATE" => Properties.updateFile(messageText.substring(0, messageText.indexOf("#")).toInt, messageText.substring(messageText.indexOf("#") + 1, messageText.indexOf(":")), messageText.substring(messageText.indexOf(":") + 1))
                            case "REMOVE" => Properties.removeFile(messageText.toInt)
                            case "REFRESH" => Properties.refreshFile(messageText.toInt)
                            case _ =>
                        }
                    case "CONNECTION" =>
                        //CONNECTION - UPSERT - connection_type.connection_name=connection_string#&#user_name#&#password
                        //CONNECTION - ENABLE/DISABLE/REMOVE - connection_name
                        messageKey match {
                            case "UPSERT" => JDBCConnection.upsert(messageText)
                            case "ENABLE" => JDBCConnection.enable(messageText)
                            case "DISABLE" => JDBCConnection.disable(messageText)
                            case "REMOVE" => JDBCConnection.remove(messageText)
                            case _ =>
                        }
                    case _ =>
                }
            }).clear()
        }
        while (Timer.rest() < nextMinute)
    }
}
