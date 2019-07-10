package io.qross.model

import java.io.File
import java.util.regex.Pattern

import io.qross.jdbc.DataSource
import io.qross.net.Json
import io.qross.time.DateTime
import io.qross.ext.TypeExt._
import io.qross.setting.Global

import scala.collection.mutable
import scala.util.{Success, Try}

object TaskDependency {
    
    def parseDependencyValue(jobId: String, taskId: String, dependencyValue: String, taskTime: String): List[String] = {
    
        var content = dependencyValue
        content = content.replace("${jobId}", jobId)
        content = content.replace("${taskId}", taskId)
        content = content.replace("%QROSS_HOME", Global.QROSS_HOME)
        
        if (content.contains("${") && content.contains("}")) {
            val values = new mutable.TreeSet[String]()
            val semi = new java.util.LinkedList[String]()
            semi.offer(content)
            
            while (!semi.isEmpty) {
                val value = semi.poll()
                val ahead = value.substring(0, value.indexOf("${"))
                var exp = value.substring(value.indexOf("${") + 2)
                val latter = exp.substring(exp.indexOf("}") + 1)
                exp = exp.substring(0, exp.indexOf("}"))
                
                DateTime(taskTime).shark(exp).foreach(value => {
                        val replacement = ahead + value + latter
                        if (latter.contains("${") && latter.contains("}")) {
                            semi.offer(replacement)
                        }
                        else {
                            values += replacement
                        }
                    })
            }
            
            values.toList
        }
        else {
            List[String](content)
        }
    }

    def check(dependencyType: String, dependencyValue: String, taskId: Long, recordTime: String): (String, String) =  {
        
        var ready = "no"
        val conf = Json(dependencyValue).parseRow("/")
        
        dependencyType.toUpperCase() match {
            
            /*
            {
                "dataSource": "source name"
                "selectSQL": "select SQL"
                "updateSQL": "update SQL, support # place holder",
                "field": "", //如果不设置, 默认第一个字段
                "operator": "==", //如果不设置，默认"="
                "value": 0  //如果不设置，则有数据则依赖成立
            }             */
            case "SQL" =>
                    val ds = new DataSource(conf.getString("dataSource"))
                    val table = ds.executeDataTable(conf.getString("selectSQL"))
                    if (table.nonEmpty) {

                        val field = conf.getString("field", "").trim
                        val compareValue = conf.getString("value", "").trim
                        if (compareValue.nonEmpty) {
                            val currentValue = table.lastRow.get.getStringOption(if ("""^\d+$""".r.findFirstIn(field).nonEmpty) field.toInt - 1 else field).getOrElse("NULL")
                            if (conf.getString("operator", "==").trim match {
                                case "=" | "==" => currentValue == compareValue
                                case "!=" | "<>" => currentValue != compareValue
                                case "^=" => currentValue.startsWith(compareValue)
                                case "$=" => currentValue.endsWith(compareValue)
                                case "*=" => currentValue.contains(compareValue)
                                case "#=" => Pattern.matches(compareValue, currentValue) //regex match
                                case operator =>
                                    (Try(currentValue.toDouble), Try(compareValue.toDouble)) match {
                                        case (Success(v1), Success(v2)) =>
                                            operator match {
                                                case ">" => v1 > v2
                                                case ">=" => v1 >= v2
                                                case "<" => v1 < v2
                                                case "<=" => v1 < v2
                                                case _ => false
                                            }
                                        case _ => false
                                    }
                                }) {
                                    ready = "yes"
                            }
                        }
                        else {
                            ready = "yes"
                        }

                        if (ready == "yes") {
                            if (conf.contains("updateSQL") && conf.getString("updateSQL") != "") {
                                ds.tableUpdate(conf.getString("updateSQL"), table)
                            }
                            conf.set("SELECT", table.count())

                            //pass variables to command in pre-dependency
                            table.lastRow match {
                                case Some(row) =>
                                    val df = new DataSource()
                                    row.getFields.foreach(field => {
                                        df.addBatchCommand(s"UPDATE qross_tasks_dags SET command_text=REPLACE(command_text, '#{$field}', '${row.getString(field).replace("'", "''")}') WHERE task_id=$taskId AND record_time='$recordTime' AND POSITION('#{' IN command_text) > 0")
                                    })
                                    df.executeBatchCommands()
                                    df.close()
                                case None =>
                            }
                        }
                    }
                    else {
                        conf.set("SELECT", "EMPTY")
                    }
                    table.clear()
                    ds.close()
                    
            /*
            {
                "path": ""
                "minAmount": 1
                "minLength": "10M"
                "lastModifiedTimeSpan": "5m"
            }
             */
                /* to be remove to Cluster Version
            case "HDFS" =>
                val files = HDFS.list(conf.getString("path"))
                conf.set("amount", files.size)
                if (files.size >= conf.getLongOption("minAmount").getOrElse(1L)) {
                    var minSize = 0L
                    var lastModified = 0L
                    for (file <- files) {
                        if (minSize == 0 || minSize < file.size) {
                            minSize = file.size
                        }
                        if (lastModified == 0 || lastModified > file.lastModificationTime) {
                            lastModified = file.lastModificationTime
                        }
                    }
                    conf.set("size", FileLength.toHumanizedString(minSize))
                    conf.set("lastModified", lastModified / 1000)
                
                    ready = "yes"
                    
                    if (conf.contains("minLength")) {
                        if (minSize < FileLength.toByteLength(conf.getString("minLength"))) {
                            ready = "no"
                        }
                    }
                    
                    if (conf.contains("lastModifiedTimeSpan")) {
                        if (System.currentTimeMillis() - lastModified < conf.getLongOption("lastModifiedTimeSpan").getOrElse(60L) * 1000L) {
                            ready = "no"
                        }
                    }
                } */
        
            /*
            {
                "url": "",
                "post": "",
                "path": "",
                "operator": "",
                "value": ""
            }
            */
            case "API" =>
                val json = Json().readURL(conf.getString("url"), conf.getString("post", ""))
                
                val currentValue = json.parseValue(conf.getString("path")).toString
                val compareValue = conf.getString("value")
                if (conf.getString("operator", "==").trim match {
                    case "=" | "==" => currentValue == compareValue
                    case "!=" | "<>" => currentValue != compareValue
                    case "^=" => currentValue.startsWith(compareValue)
                    case "$=" => currentValue.endsWith(compareValue)
                    case "*=" => currentValue.contains(compareValue)
                    case "#=" => Pattern.matches(compareValue, currentValue) //regex match
                    //case "?=" => Pattern.matches(compareValue, currentValue) //regex match
                    //case "*=" => Pattern.compile(compareValue, Pattern.CASE_INSENSITIVE).matcher(currentValue).matches() //regex math
                    case operator =>
                        (Try(currentValue.toDouble), Try(compareValue.toDouble)) match {
                            case (Success(v1), Success(v2)) =>
                                operator match {
                                    case ">" => v1 > v2
                                    case ">=" => v1 >= v2
                                    case "<" => v1 < v2
                                    case "<=" => v1 < v2
                                    case _ => false
                                }
                            case _ => false
                        }
                }) {
                    ready = "yes"
                }
        
            /*
            {
                "jobId": 123,
                "taskTime": "datetime sharp expression",
                "status": "finished"
            }
            */
            case "TASK" =>
                if (DataSource.queryExists("SELECT id FROM qross_tasks WHERE job_id=? AND task_time=? AND status=?",
                        conf.getInt("jobId"),
                        conf.getString("taskTime"),
                        conf.getString("status", TaskStatus.SUCCESS))) {
                    ready = "yes"
                }
        
            /*
            {
                "path": "",
                "minLength": "123K"
            }
            */
            case "FILE" =>
                val file = new File(conf.getString("path"))
                if (file.exists()) {
                    conf.set("exists", "yes")
                    conf.set("length", file.length().toHumanized)
                    if (file.length() >= conf.getString("minLength").toByteLength) {
                        ready = "yes"
                    }
                }
                else {
                    conf.set("exists", "no")
                }
                
            case _ =>
        }
    
        (ready, conf.toString)
    }
}