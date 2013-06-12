package app.generator

import app.element.Workflow
import scala.collection.mutable.LinkedHashMap
import app.element.Sequence
import scala.collection.mutable.HashSet
import scala.collection.mutable.MutableList

class Generator(val wf: Workflow) {
  private val out = new StringBuilder()
  private var indent = 0
  private var totalSignalsNum = 0
  private var totalFunctionsNum = 0
  private var totalTasksNum = 0
  
  val vars = scala.collection.mutable.Map[String, Any]()
  val signals = LinkedHashMap[String, Signal]()
  val functions = LinkedHashMap[String, Fun]()
  val tasks = LinkedHashMap[String, Task]() 
  val ins = scala.collection.mutable.MutableList[SimpleSignal]()
  val outs = scala.collection.mutable.MutableList[SimpleSignal]()

  def generate(args: List[String]): String = {
    prepareDataStructures(args)
    generateCode()
  }
  
  private def prepareDataStructures(args: List[String]) = {    
    addArgs(args)
    addVars()
    addSignals()
    addFunctions()
    addTasks()
    ins ++= resolveInsOuts(wf.ins)
    outs ++= resolveInsOuts(wf.outs)
    ""
  }
  
  private def generateCode() = {
    append("{")
    indent += 1
    printName()
    printFunctions()
    printTasks()
    printSignals()
    printInsOuts("ins")
    printInsOuts("outs")
    removeLastComma()
    indent -= 1
    append("}")
    out.toString()
  }

  private def printName() = {
    append("\"name\": \"" + wf.name + "\",")
  }
  
  /*
   * Binds generator invocation args to workflow args and adds them to vars
   */
  private def addArgs(args: List[String]) = {
    if (args.size != wf.args.size) {
      throw new Exception("Incorrect number of workflow arguments")
    }
    if (wf.args.distinct.size != wf.args.size) {
      throw new Exception("Workflow arguments have to be uniquely named")
    }
    val parsedArgs = args map (arg => evalArg(arg))
    vars ++= (wf.args zip parsedArgs).toMap[String, Any]
  }
  
  /*
   * "1" -> Int
   * "1.0" -> Double
   * "'a'" -> Char
   * ""str"" -> String
   */
  private def evalArg(arg: String): Any = {
    parse[Int](arg) match {
      case Some(i) => i
      case None => parse[Double](arg) match {
        case Some(d) => d
        case None => 
          if (arg.size == 3 && arg.charAt(0) == '\'' && arg.charAt(2) == '\'')
            arg.charAt(1)
          else if (arg.size >= 2 && arg.charAt(0) == '"' && arg.charAt(arg.size-1) == '"')
            arg.substring(1, arg.size - 1)
          else
            throw new Exception("Invalid argument format " + arg)
      }
    }
  }

  /*
   * evaluates variables declared in vars section of code
   */
  private def addVars() = {
  	for ((name, value) <- wf.vars) {
  	  if (vars.contains(name)) {
  	    throw new Exception("Variable " + name + " specified twice")
  	  }
  	  if (name == "i") {
  	    throw new Exception("'i' is a reserved variable name and cannot be declared")
  	  }
  	  try {
  	  	vars += name -> evalVar(value)
  	  } catch {
  	    case e: Throwable => throw new Exception(e.getMessage() + " in the definition of " + name)
  	  }
  	}
//  	println(vars)
//  	println(vars map Function.tupled((k, v) => v match {
//  	  case t: Tuple2[Any, Any] => t._2.getClass
//  	  case other => other
//  	}))
  }
  
  /*
   * Sequence(from, to, step) - implicitly defined sequence, will be unfolded to List
   * (a) - variable named a
   * (a, b) - a[b]
   * const - primitive | explicit sequence e.g. {"a", "c", "f"}
   */
  def evalVar(value: Any): Any = {
    value match {
      case Sequence(from, to, step) => evalSeq(evalVar(from), evalVar(to), evalVar(step))
      case Tuple1(a: String) => vars.get(a) match {
        case Some(res) => res
        case _ => throw new Exception("Variable " + a + " undefined")
      }
      case Tuple1(a) => evalVar(a)
      case (a, b) => (evalVar(a), evalVar(b)) match {
        case (seq: List[Any], index: Int) => 
          if (seq.size > index) seq(index) 
          else throw new Exception("Index " + index + " is out of bounds of sequence " + a + " of size " + seq.size)
        case (seq: List[Any], _) => throw new Exception(a + " cannot take " + b + " as an index")
        case _ => throw new Exception(a + " is not a sequence and cannot be accessed with [] operator")
      }
      case const => const
    }
  }
  
  /*
   * Evaluate a variable that uses the special variable "i"
   */
  def evalVar(value: Any, i: Int): Any = {
    vars += "i" -> i
    val res = evalVar(value)
    vars -= "i"
    res
  }
  
  /*
   * Unfolds the {from ... to ... step ...} expressions to appropriate Lists
   */
  private def evalSeq(seq: Any): List[Any] = {
    seq match {
      case (from: Int, to: Int, step: Int) => (from.to(to).by(step)).toList
      case (from: Char, to: Char, step: Int) => (from.to(to).by(step.toChar)).toList
      case other => throw new Exception("Badly defined sequence " + other)
    }
  }
  
  private def addSignals() = {
    var index = 0
    val signalNames: List[String] = wf.signals map Function.tupled((name, _, _) => name)
    if (signalNames.distinct.size != signalNames.size) {
      throw new Exception("Signal names have to be unique")
    }
    for(signal <- wf.signals) {
      signal match {
        case (name, null, args) => {
          signals += name -> new Signal(name, null, args, index, this)
          index += 1
        }
        case (name, seq, args) => vars.get(seq) match {
          case Some(l: List[Any]) => {
            signals += name -> new Signal(name, l, args, index, this)
            index += l.size
          }
          case _ => throw new Exception("The generating sequence in signal " + name + " is incorrect")
        }
      }
    }
    totalSignalsNum = index
  }
  
  private def printSignals() = {
    append("\"data\": [")
    indent += 1
    for ((defName, signal) <- signals) {
      if (signal.genSeq == null) {
        append("{")
        indent += 1
        val resolvedArgs = signal.getResolvedArgs()
          resolvedArgs.find(Function.tupled((n, v) => n == "name")) match {
          	case Some((n, v)) => append("\"name\": \"" + v + "\",")
	          case _ => append("\"name\": \"" + defName + "\",")
	        }
          for ((k, v) <- resolvedArgs.filterNot(Function.tupled((n,v) => n == "name"))) {
	          append("\"" + k + "\": \"" + v + "\",")
	        }
          removeLastComma()
        indent -= 1
        append("},")
      }
      else {
        var idx = 0
        for (elem <- signal.genSeq) {
          append("{")
          indent += 1
          val resolvedArgs = signal.getResolvedArgs(idx)
          resolvedArgs.find(Function.tupled((n, v) => n == "name")) match {
          	case Some(Tuple2(n, v)) => append("\"name\": \"" + v + "\",")
	          case _ => append("\"name\": \"" + defName + "\",")
	        }
          for ((k, v) <- resolvedArgs.filterNot(Function.tupled((n,v) => n == "name"))) {
	          append("\"" + k + "\": \"" + v + "\",")
	        }
          removeLastComma()
          idx += 1
	        indent -= 1
	        append("},")
        }
      }
    }
    if (totalSignalsNum > 0) {
      removeLastComma()
    }
    indent -= 1
    append("],")
  }
  
  private def addFunctions() = {
    var index = 0
    if (wf.functions.distinct.size != wf.functions.size) {
      throw new Exception("Workflow functions have to be unique")
    }
    for((module: String, name: String) <- wf.functions) {
      functions += String.format("%s.%s", module, name) -> new Fun(module, name, index)
      index += 1
    }
    totalFunctionsNum = index
//    println(functions)
  }
  
  private def printFunctions() = {
    append("\"functions\": [")
    indent += 1
    for ((_, Fun(module, name, _)) <- functions) {
      append("{")
      indent += 1
      append("\"name\": \"" + name + "\",")
      append("\"module\": \"" + module + "\"")
      indent -= 1
      append("},")
    }
    if (totalFunctionsNum > 0) {
      removeLastComma()
    }
    indent -= 1
    append("],")
  }
  
  private def addTasks() = {
    var index = 0
    val taskNames: List[String] = wf.tasks map Function.tupled((_, name, _, _) => name)
    if (taskNames.distinct.size != taskNames.size) {
      throw new Exception("Task names have to be unique")
    }
    for(task <- wf.tasks) {
      task match {
        case (taskType, name, null, args) => {
          tasks += name -> new Task(taskType, name, null, args, index, this)
          index += 1
        }
        case (taskType, name, seq, args) => vars.get(seq) match {
          case Some(l: List[Any]) => {
            tasks += name -> new Task(taskType, name, l, args, index, this)
            index += l.size
          }
          case _ => throw new Exception("The generating sequence in task " + name + " is incorrect")
        }
      }
    }
    totalTasksNum = index
  }
  
  private def printTasks() = {
    append("\"tasks\": [")
    indent += 1
    for ((defName, task) <- tasks) {
      if (task.genSeq == null) {
        append("{")
        indent += 1
        val resolvedArgs = task.getResolvedArgs()
          resolvedArgs.find(Function.tupled((n, v) => n == "name")) match {
          	case Some((n, v)) => append("\"name\": \"" + v + "\",")
	          case _ => append("\"name\": \"" + defName + "\",")
	        }
        	append("\"type\": \"" + task.taskType + "\",")
          for ((k, v) <- resolvedArgs.filterNot(Function.tupled((n,v) => n == "name"))) {
	          append("\"" + k + "\": \"" + v + "\",")
	        }
        	val insIndexes = task.getSignalsSpec("ins") map (simpleSignal => simpleSignal.globalIndex)
          append("\"ins\": [" + insIndexes.mkString(", ") + "],")
          val outsIndexes = task.getSignalsSpec("outs") map (simpleSignal => simpleSignal.globalIndex)
          append("\"outs\": [" + outsIndexes.mkString(", ") + "],")
          removeLastComma()
        indent -= 1
        append("},")
      }
      else {
        var idx = 0
        for (elem <- task.genSeq) {
          append("{")
          indent += 1
          val resolvedArgs = task.getResolvedArgs(idx)
          resolvedArgs.find(Function.tupled((n, v) => n == "name")) match {
          	case Some(Tuple2(n, v)) => append("\"name\": \"" + v + "\",")
	          case _ => append("\"name\": \"" + defName + "\",")
	        }
          for ((k, v) <- resolvedArgs.filterNot(Function.tupled((n,v) => n == "name"))) {
	          append("\"" + k + "\": \"" + v + "\",")
	        }
          val insIndexes = task.getSignalsSpec("ins", idx) map (simpleSignal => simpleSignal.globalIndex)
          append("\"ins\": [" + insIndexes.mkString(", ") + "],")
          val outsIndexes = task.getSignalsSpec("outs", idx) map (simpleSignal => simpleSignal.globalIndex)
          append("\"outs\": [" + outsIndexes.mkString(", ") + "],")
          removeLastComma()
          idx += 1
	        indent -= 1
	        append("},")
        }
      }
    }
    if (totalSignalsNum > 0) {
      removeLastComma()
    }
    indent -= 1
    append("],")
  }
  
  def evalSignal(value: Any, i: Int): List[SimpleSignal] = {
    vars += "i" -> i
    val res = evalSignal(value)
    vars -= "i"
    res
  }
  
  def evalSignal(value: Any): List[SimpleSignal] = {
    value match {
      case Tuple1(name: String) => signals.get(name) match {
        case Some(s: Signal) => s match {
          case Signal(_, null, _, globalIndex, _) => List(SimpleSignal(globalIndex, s))
          case _ => throw new Exception("Cannot assign the sequence based signal " + name + " to a single port")
        }
        case None => throw new Exception("Undeclared signal " + name)
      }
      case Tuple2(Tuple1(name: String), null) => signals.get(name) match {
        case Some(s: Signal) => s match {
          case Signal(_, null, _, _, _) => throw new Exception("Cannot flatten a sequence in a non-sequence based signal " + name)
          case Signal(_, seq, _, globalIndex, _) => {
            val l = MutableList[SimpleSignal]()
            var index = 0
            for (elem <- seq) {
              l += SimpleSignal(globalIndex + index, s)
              index += 1
            }
            l.toList
          }
        }
        case None => throw new Exception("Undeclared signal " + name)
      }
      case Tuple2(Tuple1(name: String), unresolvedIndex) => signals.get(name) match {
        case Some(s: Signal) => s match {
          case Signal(_, null, _, _, _) => throw new Exception("Cannot access an index in a non-sequence based signal " + name)
          case Signal(_, seq, _, globalIndex, _) => {
            evalVar(unresolvedIndex) match {
              case index: Int => {
              	if (seq.size <= index) throw new Exception("The index " + unresolvedIndex + " is equal to " + index + " and is out of bounds of the base-sequence of signal " + name + " of size " + seq.size)
              	List(SimpleSignal(globalIndex + index, s))
              }
              case _ => throw new Exception("The index " + unresolvedIndex + " is not a variable of Int type in signal " + name)
            }
          }
        }
        case None => throw new Exception("Undeclared signal " + name)
      }
    }
  }
  
  def resolveInsOuts(io: List[Any]): List[SimpleSignal] = {
    io.foldLeft(List[SimpleSignal]())((l, signalSpec) => l ++ evalSignal(signalSpec))
  }
  
  def printInsOuts(ioName: String) = {
    val io = ioName match {
      case "ins" => ins
      case "outs" => outs
    }
    val indexes = io map (simpleSignal => simpleSignal.globalIndex)
    append("\"" + ioName + "\": [" + indexes.mkString(", ") + "],")
  }

  private def append(s: String) = {
    out.append("\t" * indent)
    out.append(s)
    out.append("\n")
  }
  
  private def removeLastComma() = {
    out.deleteCharAt(out.size-2)
  }
  
  // purely for simple string parsing purposes in evalArgs()
  case class ParseOp[T](op: String => T)
  implicit val popDouble = ParseOp[Double](_.toDouble)
  implicit val popInt = ParseOp[Int](_.toInt)
  def parse[T: ParseOp](s: String) = 
    try {
      Some(implicitly[ParseOp[T]].op(s)) 
    } catch {case _: Throwable => None}

}