name := "OPAL-Developer Tools"

version := "0.8.0-SNAPSHOT"

scalacOptions in (Compile, doc) ++= Opts.doc.title("OPAL - Developer Tools") 

fork in run := true