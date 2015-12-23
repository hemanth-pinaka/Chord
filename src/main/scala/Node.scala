
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


class Node(val hashValue: Int, val numRequests: Int) extends Actor {
  
  var fingerTable = new HashMap[Int, Int]
  var successor: Int = -1;
  var predecessor: Int = -1;
  var isJoined: Boolean = false;
  var timeStamp = System.currentTimeMillis()
  var searchForPredecs = new ArrayBuffer[Int]

  var utils:Utils=new Utils()

  var numKeyToBeSearched = -1

  def receive = {
    

    

    case join(adjNode: Int)  => {

      if (adjNode == -1) {
        // Implies first Node.
        successor = hashValue;
        predecessor = hashValue;
        utils.fillFingerTable(fingerTable,hashValue,-1);
        isJoined = true              
        Chord.nodesJoined = Chord.nodesJoined + 1;        
        //self ! "printTable"
        
      } else {
        var act = context.actorSelection(Constants.namingPrefix + adjNode)
        act ! findPredecessor(hashValue, hashValue, "setRequest", null)
      }
    }

    case fg: Finger => {
      
      if (fg.Type.equals("fingerRequest")) {
        if (fg.i <= Constants.m) {
          var curIndex = fg.i + 1
          fingerTable(fg.i) = fg.node
          var nextStart: Int = (hashValue + Math.pow(2, (curIndex)).toInt) % Constants.totalSpace
          if (curIndex < Constants.m) {
            var sucActor = context.actorSelection(Constants.namingPrefix + successor)
            sucActor ! findSuccessor(nextStart, hashValue, "fingerRequest", curIndex)
          } else {
            //All neighbors are found. Nothing to do.
            
            self ! updateOthers()
          }
        } else {
          //processing is done.
        }
      }
    }

    

    case h: hasKey => {

      var found = false;

    }

    case getSuccessor(fromNewNode: Boolean) => {
      //log.debug("\tType:getSucc\t\tFrom:" + sender.path + "\tTo:" + hashName + "\t Msg: " + gs)
      if (fromNewNode) {
        sender ! setSuccessor(successor, false)
      }
    }
    
    

    case findKey(numReq: Int) => {

      var timestamp = System.currentTimeMillis()
      val scheduler = context.system.scheduler
      
      for (reqIndex <- 1 to numReq) {
        var KeyString = Constants.namingPrefix + hashValue + reqIndex;
        var KeyHash = Chord.generateHashCode(KeyString, Constants.totalSpace)
        scheduler.scheduleOnce(Duration(reqIndex, TimeUnit.SECONDS), self, querySingleKey(KeyHash))
      }
      
    }

    case setSuccessor(name: Int, fromNewNode: Boolean) => {
    //  log.debug("\tType:setSucc\t\tFrom:" + sender.path + "\tTo:" + hashName + "\t Msg: " + ss)
      if (fromNewNode && isJoined) {
        var myOldSuccesor = context.actorSelection(Constants.namingPrefix + successor)
        successor = name
        
        myOldSuccesor ! setPredecessor(name, isJoined)
      } else if (!fromNewNode && !isJoined) {
        successor = name
        
        //Since I am a new node, Update the successor info of my new predecessor
        var myNewPredecessor = context.actorSelection(Constants.namingPrefix + predecessor)
        myNewPredecessor ! setSuccessor(hashValue, !isJoined)
        var f = Future {
          Thread.sleep(10)
        }
        f.onComplete {
          case x =>
            
            fingerTable(0) = successor
            self.tell(Finger(-1, fingerTable(0), "fingerRequest", 0), self)
        }
      }
    }
    
    case uo: updateOthers => {
      var preActor = context.actorSelection(Constants.namingPrefix + predecessor)
      var updateableNodes = new HashMap[Int, Int]
      for (i <- 0 until Constants.m) {
        var temp = -1
        var pow = Math.pow(2, i).toInt;
        if (pow < hashValue) {
          temp = (hashValue - pow) % Constants.totalSpace
        } else {
          temp = Constants.totalSpace - Math.abs(hashValue - pow)
        }
        updateableNodes.put(i, temp);
      }
      
      for (i <- 0 until Constants.m) {
        var t = updateableNodes(i)
        if (!utils.isPredecessor(t,hashValue,successor)) {
          preActor ! findPredecessor(updateableNodes(i), hashValue, "fingerUpdate", "" + i)
        }
      }
      //log.info(hashValue + "joined with succ : " + successor + " and predec: " + predecessor)
      println("Successor = "+successor)
      println("Predecessor = "+predecessor)
      isJoined = true;
      Chord.nodesJoined = Chord.nodesJoined + 1;
      var temp = Chord.isNodeDone.get()  
      Chord.isNodeDone.set(!temp)
    }

    case setPredecessor(name: Int, fromNewNode: Boolean) => {
      //log.debug("\tType:setPredec\t\tFrom:" + sender.path + "\tTo:" + hashName + "\t Msg: " + p)
      if (isJoined && fromNewNode) {
        predecessor = name;
        
      } else if (!isJoined && !fromNewNode) {
        predecessor = name
        
        var act = context.actorSelection(Constants.namingPrefix + predecessor)
        act ! getSuccessor(!isJoined)
      }
    }

    case f: findPredecessor => {
      
      
      var start = hashValue
      var end = successor
      var conditionA = (start == end)
      var conditionB = (end > start && (f.key >= start && f.key < end))
      var conditionC = (end < start && ((f.key >= start && f.key < Constants.totalSpace) || (f.key >= 0 && f.key < end)))

      if (f.reqType.equals("setRequest")) {
        if (conditionA || conditionB || conditionC) {
          
          var act = context.actorSelection(Constants.namingPrefix + f.origin)
          act ! setPredecessor(start, false)
        } else {
          var closetNeigh = utils.closestPrecedingFinger(f.key,fingerTable);
          if (fingerTable(closetNeigh) == hashValue) {
            var act = context.actorSelection(Constants.namingPrefix + f.origin)
            act ! setPredecessor(start, false)
          } else {
            var act = context.actorSelection(Constants.namingPrefix + fingerTable(closetNeigh))
            act ! f
          }
        }
      } else if (f.reqType.equals("fingerUpdate")) {
        
        if (conditionA || conditionB || conditionC) {
          var c1 = (fingerTable(f.data.toInt) > f.origin)
          var c2 = ((fingerTable(f.data.toInt) < f.origin) && (fingerTable(f.data.toInt) <= hashValue))
          
          if (c1 || c2) {
            
            fingerTable(f.data.toInt) = f.origin
          }
        } else {
          // Check if you are the successor. If you are the successor, then your predecessor is the actual predecessor for this key.
          var act = null
          if (utils.isSuccessor(f.key,predecessor,hashValue)) {
            //Route the query to your predecessor, it will find itself.
            var act = context.actorSelection(Constants.namingPrefix + predecessor)
            act ! f
          } else {
            var closetNeigh = utils.closestPrecedingFinger(f.key,fingerTable);
            var act = context.actorSelection(Constants.namingPrefix + fingerTable(closetNeigh))
            act ! f
          }
        }
      }
    }

    case s: findSuccessor => {
      if (s.Type.equals("fingerRequest")) {
        
        var start = hashValue
        var end = successor

        var conditionA = (start == end)
        var conditionB = (end > start && (s.key >= start && s.key < end))
        var conditionC = (end < start && ((s.key >= start && s.key < Constants.totalSpace) || (s.key >= 0 && s.key < end)))

        var conditionD = false
        if (predecessor < hashValue)
          conditionD = (predecessor < s.key && s.key < hashValue)
        else {
          var cond1 = (predecessor < s.key && s.key < Constants.totalSpace)
          var cond2 = (0 < s.key && s.key < hashValue)
          conditionD = (cond1 && !cond2) || (!cond1 && cond2)
        }
        

        if (conditionA || conditionB || conditionC) {
          // My successor is the succesor of the given key 
          var act = context.actorSelection(Constants.namingPrefix + s.origin)
          act ! Finger(s.key, end, s.Type, s.i)
        } else if (conditionD) {
          // I am the successor
          var act = context.actorSelection(Constants.namingPrefix + s.origin)
          act ! Finger(s.key, hashValue, s.Type, s.i)
        } else {
          var closetNeigh = utils.closestPrecedingFinger(s.key,fingerTable);
          
          if (fingerTable(closetNeigh) == hashValue) {
            var act = context.actorSelection(Constants.namingPrefix + s.origin)
            act ! Finger(s.key, fingerTable(closetNeigh), s.Type, s.i)
          } else {
            var act = context.actorSelection(Constants.namingPrefix + fingerTable(closetNeigh))
            act ! findSuccessor(s.key, s.origin, s.Type, s.i)
          }
        }
      }
    }
    
    case querySingleKey(keyHash: Int) => {
      
      var fileHashName = keyHash
      var found: Boolean = false;
      var maxPredecessor: Int = -1;
      Chord.TotalHops = Chord.TotalHops + 1;

      var isForwarded: Boolean = false
      if (utils.isSuccessor(fileHashName,predecessor,hashValue)) {
        utils.foundKey(fileHashName, hashValue)
        found = true;
      }

      if (!found && utils.isPredecessor(fileHashName,hashValue,successor)) {
        var act = context.actorSelection(Constants.namingPrefix + successor)
        act ! querySingleKey(fileHashName)
        isForwarded = true
      }

      if (!found && !isForwarded) {
        var act = context.actorSelection(Constants.namingPrefix + fingerTable(utils.closestPrecedingFinger(fileHashName,fingerTable)))
        act ! querySingleKey(fileHashName)
      }
    }

    case str: String => {
      if (str.equals("print")) {
        
      } 
    }

  }
}



  