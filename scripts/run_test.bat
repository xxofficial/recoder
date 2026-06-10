@echo off
set "JAVA_HOME=D:\Software\Program\Android Studio\jbr"
call .\gradlew.bat cleanTestDebugUnitTest :app:testDebugUnitTest --tests com.recoder.stockledger.data.importer.USmartStatementParserTest
