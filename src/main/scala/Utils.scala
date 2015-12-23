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

/**
 * @author hemanth
 */
class Utils {
  
  def fillFingerTable(fingerTable:HashMap[Int,Int],hashValue:Int,myRandNeigh: Int): Unit = {
    if (myRandNeigh == -1) {
      for (i <- 0 until Constants.m) {
        fingerTable(i) = hashValue
      }
    }
  }
  
  def isSuccessor(key: Int,predecessor:Int,hashValue:Int): Boolean = {

    var isSuccessor = false
    if (predecessor < hashValue) {
      isSuccessor = (predecessor <= key) && (key < hashValue)
    } else {
      var cond1 = (predecessor <= key) && (key < Constants.totalSpace)
      var cond2 = (0 < key) && (key < hashValue)
      isSuccessor = (cond1 && !cond2) || (!cond1 && cond2)
    }
    
    isSuccessor
  }

  def isPredecessor(key: Int,hashValue:Int,successor:Int): Boolean = {

    var isPredecessor = false
    if (successor > hashValue) {
      isPredecessor = (key <= successor) && (key > hashValue)
    } else {
      var cond1 = (hashValue < key) && (key < Constants.totalSpace)
      var cond2 = (0 < key) && (key < successor)
      isPredecessor = (cond1 && !cond2) || (!cond1 && cond2)
    }
    
    isPredecessor
  }

  def closestPrecedingFinger(key: Int,fingerTable:HashMap[Int,Int]): Int = {
    var keyFound = Integer.MIN_VALUE
    var farthestNeigh = Integer.MIN_VALUE
    var current = Integer.MIN_VALUE;

    var negativeNeigh = Integer.MAX_VALUE
    var positiveNeigh = Integer.MAX_VALUE
    var positiveValFound = false

    for (fingerIndex <- 0 until fingerTable.size) {

      var diff = key - fingerTable(fingerIndex)
      if (0 < diff && diff < positiveNeigh) {
        keyFound = fingerIndex;
        positiveNeigh = diff
        positiveValFound = true
      } else if (diff < 0 && diff < negativeNeigh && !positiveValFound) {
        keyFound = fingerIndex;
        negativeNeigh = diff
      }
    }
    keyFound
  }
  
  def foundKey(fileName: Int, foundAt: Int): Unit = {
    Chord.incrementCounter()
    Chord.keyFound(fileName) = foundAt
  }
  
  
}