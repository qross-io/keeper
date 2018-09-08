package io.qross.util

import java.io._

import io.qross.model.Global

import scala.collection.immutable.HashMap
import scala.util.{Success, Try}

object Properties {
    
    private val props = new java.util.Properties()
    private val externalPath = new File(Properties.getClass.getProtectionDomain.getCodeSource.getLocation.getPath).getParentFile.getAbsolutePath.replace("\\", "/") + "/qross.properties"
    //private lazy val externalOutput = new FileOutputStream(internalPath)
    
    if (!loadLocalFile(externalPath)) {
        loadResourcesFile("/conf.properties")
    }
    
    def loadAll(files: String*): Unit = {
        //load all files specified at args
        files.foreach(path => {
            loadLocalFile(path)
        })
        
        if (!props.containsKey(DataSource.DEFAULT)) {
            Output.writeException(s"Can't find properties key ${DataSource.DEFAULT}, it must be set in conf.properties or qross.properties.")
            System.exit(1)
        }
        else if (!DataSource.testConnection()) {
            Output.writeException(s"Can't open database, please check your connection string of ${DataSource.DEFAULT}.")
            System.exit(1)
        }
        else {
            var version = ""
            try {
                version = Global.QROSS_VERSION
            }
            catch {
                case e: Exception => e.printStackTrace()
            }
            
            if (version != "") {
                Output.writeMessage("Welcome to QROSS Keeper v" + version)
            }
            else {
                Output.writeException("Can't find Qross system, please create your qross system use Qross Master.")
                System.exit(1)
            }
        }
    
        DataSource.queryDataTable("SELECT id, properties_type, properties_path FROM qross_properties WHERE id>2").foreach(row => {
            load(row.getString("properties_type"), row.getString("properties_path"))
        }).clear()
    }
    
    def load(propertiesType: String, propertiesPath: String): Boolean = {
        if (propertiesType == "local") {
            loadLocalFile(propertiesPath)
        }
        else {
            loadResourcesFile(propertiesPath)
        }
    }
    
    def loadLocalFile(path: String): Boolean = {
        val file = new File(path)
        if (file.exists()) {
            props.load(new BufferedInputStream(new FileInputStream(file)))
            true
        }
        else {
            false
        }
    }
    
    def loadResourcesFile(path: String): Boolean = {
        try {
            props.load(new BufferedReader(new InputStreamReader(Properties.getClass.getResourceAsStream(path))))
            true
        }
        catch {
            case _ : Exception => false
        }
    }
    
    def get(key: String, defaultValue: String = ""): String = {
        if (props.containsKey(key)) {
            props.getProperty(key)
        }
        else {
            defaultValue
        }
    }
    
    def contains(key: String): Boolean = {
        props.containsKey(key)
    }
    
    /*
    def set(key: String, value: String): Unit = {
        props.setProperty(key, value)
        props.store(externalOutput, "updated by user: " + key + " = " + value)
    }
    */
    
    def addFile(propertiesType: String, propertiesPath: String): Unit = {
        DataSource.queryUpdate("INSERT INTO qross_properties (properties_type, properties_path) SELECT ?, ? FROM dual WHERE NOT EXISTS (SELECT id FROM qross_properties WHERE properties_type=? AND properties_path=?)",
            propertiesType, propertiesPath, propertiesType, propertiesPath)
    
        load(propertiesType, propertiesPath)
    }
    
    def removeFile(propertiesId: Int): Unit = {
        DataSource.queryUpdate("DELETE FROM qross_properties WHERE id=?", propertiesId)
        
        loadAll()
    }
    
    def updateFile(propertiesId: Int, propertiesType: String, propertiesPath: String): Unit = {
        DataSource.queryUpdate("UPDATE qross_properties SET properties_type=?, properties_path=? WHERE id=?", propertiesType, propertiesPath, propertiesId)
        load(propertiesType, propertiesPath)
    }
    
    def refreshFile(propertiesId: Int): Unit = {
        val row = DataSource.queryDataRow("SELECT properties_type, properties_path FROM qross_properties WHERE id=?", propertiesId)
        load(row.getString("properties_type"), row.getString("properties_path"))
        row.clear()
    }
}