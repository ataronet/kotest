package io.kotest.runner.console

import io.kotest.core.spec.Spec
import io.kotest.core.spec.description
import io.kotest.core.test.Description
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.core.test.TestStatus
import io.kotest.core.test.TestType
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KClass

class TeamCityConsoleWriter(private val prefix: String? = null) : ConsoleWriter {

   private var errors = false

   private fun locationHint(testCase: TestCase) =
      "kotest://" + testCase.spec.javaClass.canonicalName + ":" + testCase.source.lineNumber

   private fun locationHint(kclass: KClass<out Spec>) =
      "kotest://" + kclass.java.canonicalName + ":1"

   // intellij has no support for failed suites, so if a container or spec fails we must insert
   // a dummy "test" in order to show something is red or yellow.
   private fun insertDummyFailure(desc: Description, t: Throwable?) {
      val initName = desc.name + " <init>"
      println(TeamCityMessages.testStarted(prefix, initName))
      // we must print out the stack trace in between the dummy so it appears when you click on the test name
      if (t != null) printStackTrace(t)
      val message = t?.message?.let { if (it.lines().size == 1) it else null } ?: "Spec failed"
      println(TeamCityMessages.testFailed(prefix, initName).message(message))
   }

   private fun printStackTrace(t: Throwable) {
      val writer = StringWriter()
      t.printStackTrace(PrintWriter(writer))
      System.err.println()
      System.err.println(writer.toString())
      System.err.flush()
   }

   override fun hasErrors(): Boolean = errors

   override fun specStarted(kclass: KClass<out Spec>) {
      println()
      println(
         TeamCityMessages
            .testSuiteStarted(prefix, kclass.description().name)
            .locationHint(locationHint(kclass))
      )
   }

   override fun specFinished(kclass: KClass<out Spec>, t: Throwable?, results: Map<TestCase, TestResult>) {
      println()
      val desc = kclass.description()
      if (t == null) {
         println(TeamCityMessages.testSuiteFinished(prefix, desc.name))
      } else {
         errors = true
         insertDummyFailure(desc, t)
         println(TeamCityMessages.testSuiteFinished(prefix, desc.name))
      }
   }

   override fun testStarted(testCase: TestCase) {
      if (testCase.type == TestType.Container) {
         println()
         println(
            TeamCityMessages
               .testSuiteStarted(prefix, testCase.description.name)
               .locationHint(locationHint(testCase))
         )
      } else {
         println()
         println(
            TeamCityMessages
               .testStarted(prefix, testCase.description.name)
               .locationHint(locationHint(testCase))
         )
      }
   }

   override fun testFinished(testCase: TestCase, result: TestResult) {
      val desc = testCase.description
      println()
      when (result.status) {
         TestStatus.Failure, TestStatus.Error -> {
            errors = true
            when (testCase.type) {
               TestType.Container -> {
                  insertDummyFailure(desc, result.error)
                  println(
                     TeamCityMessages
                        .testSuiteFinished(prefix, desc.name)
                        .duration(result.duration)
                  )
               }
               TestType.Test -> {
                  //result.error?.apply { printStackTrace(this) }
                  println(
                     TeamCityMessages
                        .testFailed(prefix, desc.name)
                        .withException(result.error)
                        .duration(result.duration)
                  )
               }
            }
         }
         TestStatus.Ignored -> {
            val msg = when (testCase.type) {
               // this will show up as green rather than ignored in intellij but that's ok, we can't rewrite the IDE!
               // update: seems to have been fixed in intellij 2020
               TestType.Container -> TeamCityMessages.testSuiteFinished(prefix, desc.name)
               TestType.Test -> TeamCityMessages.testIgnored(prefix, desc.name)
                  .ignoreComment(result.reason ?: "No reason")
            }
            println(msg)
         }
         TestStatus.Success -> {
            val msg = when (testCase.type) {
               TestType.Container -> TeamCityMessages.testSuiteFinished(prefix, desc.name).duration(result.duration)
               TestType.Test -> TeamCityMessages.testFinished(prefix, desc.name).duration(result.duration)
            }
            println(msg)
         }
      }
   }
}
