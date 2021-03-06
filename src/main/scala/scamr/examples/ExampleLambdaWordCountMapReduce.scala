package scamr.examples

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.{LongWritable, Text}
import scamr.MapReduceMain
import scamr.conf.{SetConfigParam, ConfigureSpeculativeExecution, LambdaJobModifier}
import scamr.io.InputOutput
import scamr.mapreduce.lambda.{LambdaReduceContext, LambdaMapContext}
import scamr.mapreduce.{MapReducePipeline, MapReduceJob}


/* This example demonstrates an MR job that uses ScaMR's lambda-based mapper and reducer. */
object ExampleLambdaWordCountMapReduce extends MapReduceMain {
  private val One = new LongWritable(1L)

  def map(input: Iterator[(Any, Text)], context: LambdaMapContext): Iterator[(Text, LongWritable)] =
    input.flatMap { case (offset, line) =>
      line.toString.split("\\s+").filterNot { _.isEmpty }.map { word => (new Text(word), One) }
    }

  def reduce(input: Iterator[(Text, Iterator[LongWritable])],
             context: LambdaReduceContext): Iterator[(Text, LongWritable)] =
    input.map { case (word, counts) =>
      val sum = counts.foldLeft(0L) { (runningSum, nextCount) => runningSum + nextCount.get }
      (word, new LongWritable(sum))
    }

  override def run(conf: Configuration, args: Array[String]): Int = {
    val inputDirs = args.init
    val outputDir = args.last

    val pipeline = MapReducePipeline.init(conf) -->  // hint: start by adding a data source with -->
      new InputOutput.TextFileSource(inputDirs) -->  // hint: use --> to direct input into or out of a stage
      new MapReduceJob(map _, reduce _, reduce _, "ScaMR lambda word count example") ++
      ConfigureSpeculativeExecution(false, false) ++  // hint: use ++ to add (Conf|Job)Modifiers to a TaskStage
      SetConfigParam("mapred.max.tracker.failures" -> 2,
                     "mapred.map.max.attempts" -> 2,
                     "mapred.reduce.max.attempts" -> 2) ++
      LambdaJobModifier { _.setNumReduceTasks(1) } -->
      new InputOutput.TextFileSink[Text, LongWritable](outputDir)
    if (pipeline.execute) 0 else 1
  }
}
