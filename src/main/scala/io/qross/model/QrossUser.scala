package io.qross.model

import io.qross.util.DataSource

object QrossUser {
    
    // Get users by role
    def getUsers(role: String): String = {
        DataSource.querySingleValue(s"SELECT GROUP_CONCAT(CONCAT(username, '<', email, '>') SEPARATOR ';') AS users FROM qross_users WHERE role='$role'").getOrElse("")
    }
}
