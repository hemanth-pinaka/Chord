import akka.actor.Actor
import java.security.MessageDigest
import akka.actor.ActorSystem
import akka.actor.Props
import java.lang.Long
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{ HashMap, SynchronizedMap }
import akka.actor.ActorRef
import scala.concurrent.impl.Future
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import java.util.NoSuchElementException
import com.sun.xml.internal.fastinfoset.tools.PrintTable
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import scala.collection.convert.decorateAsScala._
import scala.collection._
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

case class join(adjNode: Int) 
case class hasKey(fileKey: String)
case class getPredecessor() 
case class getSuccessor(fromNewNode: Boolean)
case class setPredecessor(name: Int, fromNewNode: Boolean)
case class setSuccessor(name: Int, fromNewNode: Boolean)
case class findPredecessor(key: Int, origin: Int, reqType: String, data: String)
case class findSuccessor(key: Int, origin: Int, Type: String, i: Int)
case class Finger(hashKey: Int, node: Int, Type: String, i: Int)
case class updateOthers()
case class updatePredecessorFingerTable() 
case class findKey(numReq: Int)
case class querySingleKey(keyHash: Int)

object Constants {
  val nodePrefix = "n"
  val m: Int = (math.ceil((math.log10(Integer.MAX_VALUE) / math.log10(2)))).toInt - 1
  val totalSpace: Int = Math.pow(2, m).toInt
  val actorSystem = "ChordSys"
  val namingPrefix = "akka://" + actorSystem + "/user/"
}

object Chord {

  var nodesJoined: Int = 0;
  //var fileFound = new HashMap[Int, Int]
  var keyFound: concurrent.Map[Int, Int] = new ConcurrentHashMap().asScala
  var CounterG: AtomicInteger = new AtomicInteger()
  var isNodeDone: AtomicBoolean = new AtomicBoolean(true)
  var TotalHops = 0;

  def incrementCounter(): Unit = {
    CounterG.incrementAndGet()
  }
  
  def generateHashCode(id: String, totalSpace: Int): Int = {

    if (id != null) {
      var key = MessageDigest.getInstance("SHA-1").digest(id.getBytes("UTF-8")).map("%02X" format _).mkString.trim()
      if (key.length() > 15) {
        key = key.substring(key.length() - 15);
      }
      (Long.parseLong(key, 16) % totalSpace).toInt
    } else
      0
  }
  

  def main(args: Array[String]): Unit = {

    var numNodes = 10;
    var numRequests = 10;
    var avgHopsinSystem = 0;
    if (args.length > 0) {
      numNodes = args(0).toInt
      numRequests = args(1).toInt
    }

    var config = """
      akka {
          loglevel = INFO
      }"""
    val system = ActorSystem(Constants.actorSystem, ConfigFactory.parseString(config))

    var firstNode: Int = -1;
    var node_1: ActorRef = null;
    for (i <- 1 to numNodes) {

      if (firstNode == -1) {

        firstNode = generateHashCode(Constants.nodePrefix + i, Constants.totalSpace)
        println("Node-1 Id = " + firstNode)
        node_1 = system.actorOf(Props(new Node(firstNode, numRequests)), firstNode.toString())
        node_1 ! new join(-1)
        Thread.sleep(1000)
      } else {
        var x = isNodeDone.get()
        var hashName = generateHashCode(Constants.nodePrefix + i, Constants.totalSpace)
        println("Node-" + i + " Id = " + hashName)
        var node = system.actorOf(Props(new Node(hashName, numRequests)), hashName.toString())
        node ! new join(firstNode)
        while (x == isNodeDone.get && nodesJoined < numNodes) {
          
          Thread.sleep(1)
        }        
      }
    }
    Thread.sleep(100)
       for (i <- 1 to numNodes) {
          Thread.sleep(10)
          var hashName = generateHashCode(Constants.nodePrefix + i, Constants.totalSpace)
          var node = system.actorSelection(Constants.namingPrefix + hashName)
          node ! "print"
          Thread.sleep(10)
        }
    var initTime = System.currentTimeMillis();
    var buffer: Int = 50
    while (Chord.nodesJoined < numNodes - buffer) {
      Thread.sleep(100)
    }
    println("Initiated all Finger Tables. Printing Results...")
    Thread.sleep(4000)
    
    var bootTime = (System.currentTimeMillis() - initTime)
    println("Total time to initate " + bootTime)

    initTime = System.currentTimeMillis();
    for (nodeIndex <- 1 to numNodes) {
      println("Key search for node : " + nodeIndex+" started")
      var hashName = generateHashCode(Constants.nodePrefix + nodeIndex, Constants.totalSpace)
      var node = system.actorSelection(Constants.namingPrefix + hashName)
      node ! new findKey(numRequests);
    }
    var bufferable: Int = (0.005 * numNodes * numRequests).toInt
    while ((CounterG.get() < (numNodes * numRequests) - bufferable)) {
      Thread.sleep(1000)
    }
    var searchTime = (System.currentTimeMillis() - initTime)
    println("Successfully searched for all requests")
    Thread.sleep(3000)
    println("RESULTS->")
    println("----------------------------------------------------------------")
    println("Number of Nodes = " + numNodes )
    println("Number of Requests = " + numRequests)
    println("Total time to join = " + bootTime.toDouble/1000+" seconds")
    println("Total search time = " + searchTime.toDouble/1000+" seconds")
    //println("Files found:           " + CounterG.get())
    println("Total number of hops:  " + TotalHops)
    println("Average number of hops:" + TotalHops.toDouble / (numNodes * numRequests))
   // println("List of files:       \n" + fileFound)
    System.exit(0)
  }
}
